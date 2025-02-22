package water.fvec;

import java.util.Arrays;

import water.*;
import water.parser.DParseTask;

// An uncompressed chunk of data, supporting an append operation
public class NewChunk extends Chunk {
  final int _cidx;
  // We can record the following (mixed) data types:
  // 1- doubles, in _ds including NaN for NA & 0; _ls==_xs==null
  // 2- scaled decimals from parsing, in _ls & _xs; _ds==null
  // 3- zero: requires _ls==0 && _xs==0
  // 4- NA: either _ls==0 && _xs==Integer.MIN_VALUE, OR _ds=NaN
  // 5- Enum: _xs==(Integer.MIN_VALUE+1) && _ds==null
  // Chunk._len is the count of elements appended
  // Sparse: if _len != _len2, then _ls/_ds are compressed to non-zero's only,
  // and _xs is the row number.  Still _len2 is count of elements including
  // zeros, and _len is count of non-zeros.
  transient long   _ls[];       // Mantissa
  transient int    _xs[];       // Exponent, or if _ls==0, NA or Enum or Rows
  transient int    _id[];       // Indeces (row numbers) of stored values, used for sparse
  transient double _ds[];       // Doubles, for inflating via doubles
  int _len2;                    // Actual rows, if the data is sparse
  int _naCnt=-1;                // Count of NA's   appended
  int _strCnt;                  // Count of Enum's appended
  int _nzCnt;                   // Count of non-zero's appended
  final int _timCnt[] = new int[ParseTime.TIME_PARSE.length]; // Count of successful time parses
  public static final int MIN_SPARSE_RATIO = 32;

  public NewChunk( Vec vec, int cidx ) { _vec = vec; _cidx = cidx; }

  // Constructor used when inflating a Chunk.
  public NewChunk( Chunk C ) {
    this(C._vec, C._vec.elem2ChunkIdx(C._start));
    _len2 = C._len;
    _len = C.sparseLen();
  }

  // Pre-sized newchunks.
  public NewChunk( Vec vec, int cidx, int len ) {
    this(vec,cidx);
    _ds = new double[len];
    Arrays.fill(_ds,Double.NaN);
    _len = _len2 = len;
  }

  // Heuristic to decide the basic type of a column
  public byte type() {
    if( _naCnt == -1 ) {        // No rollups yet?
      int nas=0, ss=0, nzs=0;
      if( _ds != null ) {
        assert _ls==null && _xs==null;
        for( int i = 0; i < _len; ++i) if( Double.isNaN(_ds[i]) ) nas++; else if( _ds[i]!=0 ) nzs++;
      } else {
        assert _ds==null;
        if( _ls != null )
          for( int i=0; i<_len; i++ )
            if( isNA2(i) ) nas++;
            else {
              if( isEnum2(i)   ) ss++;
              if( _ls[i] != 0 ) nzs++;
            }
      }
      _nzCnt=nzs;  _strCnt=ss;  _naCnt=nas;
    }
    // Now run heuristic for type
    if(_naCnt == _len2)          // All NAs ==> NA Chunk
      return AppendableVec.NA;
    if(_strCnt > 0 && _strCnt + _naCnt == _len2)
      return AppendableVec.ENUM; // All are Strings+NAs ==> Enum Chunk
    // Larger of time & numbers
    int timCnt=0; for( int t : _timCnt ) timCnt+=t;
    int nums = _len2-_naCnt-timCnt;
    return timCnt >= nums ? AppendableVec.TIME : AppendableVec.NUMBER;
  }

  protected final boolean isNA2(int idx) {
    return (_ds == null) ? (_ls[idx] == Long.MAX_VALUE && _xs[idx] == Integer.MIN_VALUE) : Double.isNaN(_ds[idx]);
  }
  protected final boolean isEnum2(int idx) {
    return _xs!=null && _xs[idx]==Integer.MIN_VALUE+1;
  }
  protected final boolean isEnum(int idx) {
    if(_id == null)return isEnum2(idx);
    int j = Arrays.binarySearch(_id,0,_len,idx);
    if(j < 0)return false;
    return isEnum2(j);
  }

  public void addEnum(int e) {append2(e,Integer.MIN_VALUE+1);}
  public void addNA  (     ) {append2(Long.MAX_VALUE,Integer.MIN_VALUE  ); }
  public void addNum (long val, int exp) {
    if(_ds != null){
      assert _ls == null;
      addNum(val*DParseTask.pow10(exp));
    } else {
      if( val == 0 ) exp = 0;// Canonicalize zero
      long t;                // Remove extra scaling
      while( exp < 0 && exp > -9999999 && (t=val/10)*10==val ) { val=t; exp++; }
      append2(val,exp);
    }
  }
  // Fast-path append double data
  public void addNum(double d) {
    if(_id == null || d != 0) {
      if(_ls != null)switch_to_doubles();
      if( _ds == null || _len >= _ds.length ) {
        append2slowd();
        // call addNum again since append2slow might have flipped to sparse
        addNum(d);
        assert _len <= _len2;
        return;
      }
      if(_id != null)_id[_len] = _len2;
      _ds[_len++] = d;
    }
    _len2++;
    assert _len <= _len2;
  }
  public final boolean sparse(){return _id != null;}
  // Append all of 'nc' onto the current NewChunk.  Kill nc.
  public void add( NewChunk nc ) {
    assert _cidx >= 0;
    assert _len <= _len2;
    assert nc._len <= nc._len2:"_len = " + nc._len + ", _len2 = " + nc._len2;
    if( nc._len2 == 0 ) return;
    if(_len2 == 0){
      _ls = nc._ls; nc._ls = null;
      _xs = nc._xs; nc._xs = null;
      _id = nc._id; nc._id = null;
      _ds = nc._ds; nc._ds = null;
      _len = nc._len;
      _len2 = nc._len2;
      return;
    }
    if(nc.sparse() != sparse()){ // for now, just make it dense
      cancel_sparse();
      nc.cancel_sparse();
    }
    if( _ds != null ) throw H2O.unimpl();
    while( _len+nc._len >= _xs.length )
      _xs = MemoryManager.arrayCopyOf(_xs,_xs.length<<1);
    _ls = MemoryManager.arrayCopyOf(_ls,_xs.length);
    System.arraycopy(nc._ls,0,_ls,_len,nc._len);
    System.arraycopy(nc._xs,0,_xs,_len,nc._len);
    if(_id != null) {
      assert nc._id != null;
      _id = MemoryManager.arrayCopyOf(_id,_xs.length);
      System.arraycopy(nc._id,0,_id,_len,nc._len);
      for(int i = _len; i < _len + nc._len; ++i) _id[i] += _len2;
    } else assert nc._id == null;

    _len += nc._len;
    _len2 += nc._len2;
    nc._ls = null;  nc._xs = null; nc._id = null; nc._len = nc._len2 = 0;
    assert _len <= _len2;
  }
  // PREpend all of 'nc' onto the current NewChunk.  Kill nc.
  public void addr( NewChunk nc ) {
    long  [] tmpl = _ls; _ls = nc._ls; nc._ls = tmpl;
    int   [] tmpi = _xs; _xs = nc._xs; nc._xs = tmpi;
             tmpi = _id; _id = nc._id; nc._id = tmpi;
    double[] tmpd = _ds; _ds = nc._ds; nc._ds = tmpd;
    int      tmp  = _len; _len=nc._len; nc._len=tmp;
             tmp  = _len2; _len2 = nc._len2; nc._len2 = tmp;
    add(nc);
  }

  // Fast-path append long data
  void append2( long l, int x ) {
    assert _ds == null;
    if(_id == null || l != 0){
      if(_ls == null || _len == _ls.length) {
        append2slow();
        // again call append2 since calling append2slow might have changed things (eg might have switched to sparse and l could be 0)
        append2(l,x);
        return;
      }
      _ls[_len] = l;
      _xs[_len] = x;
      if(_id  != null)_id[_len] = _len2;
      _len++;
    }
    _len2++;
    assert _len <= _len2;
  }

  // Slow-path append data
  private void append2slowd() {
    if( _len > Vec.CHUNK_SZ )
      throw new ArrayIndexOutOfBoundsException(_len);
    assert _ls==null;
    if(_ds != null && _ds.length > 0){
      if(_id == null){ // check for sparseness
        int nzs = 0; // assume one non-zero for the element currently being stored
        for(double d:_ds)if(d != 0)++nzs;
        if((nzs+1)*MIN_SPARSE_RATIO < _len2)
          set_sparse(nzs);
      } else _id = MemoryManager.arrayCopyOf(_id, _len << 1);
      _ds = MemoryManager.arrayCopyOf(_ds,_len<<1);
    } else _ds = MemoryManager.malloc8d(4);
    assert _len == 0 || _ds.length > _len:"_ds.length = " + _ds.length + ", _len = " + _len;
  }
  // Slow-path append data
  private void append2slow( ) {
    if( _len > Vec.CHUNK_SZ )
      throw new ArrayIndexOutOfBoundsException(_len);
    assert _ds==null;
    if(_ls != null && _ls.length > 0){
      if(_id == null){ // check for sparseness
        int nzs = 0;
        for(long l:_ls) if(l != 0)++nzs;
        if((nzs+1)*MIN_SPARSE_RATIO < _len2){
          set_sparse(nzs);
          assert _len == 0 || _len  <= _ls.length:"_len = " + _len + ", _ls.length = " + _ls.length + ", nzs = " + nzs +  ", len2 = " + _len2;
          assert _id.length == _ls.length;
          assert _len <= _len2;
          return;
        }
      } else {
        // verify we're still sufficiently sparse
        if((MIN_SPARSE_RATIO*(_len) >> 1) > _len2)  cancel_sparse();
        else _id = MemoryManager.arrayCopyOf(_id,_len<<1);
      }
      _ls = MemoryManager.arrayCopyOf(_ls,_len<<1);
      _xs = MemoryManager.arrayCopyOf(_xs,_len<<1);
    } else {
      _ls = MemoryManager.malloc8(4);
      _xs = MemoryManager.malloc4(4);
      _id = _id == null?null:MemoryManager.malloc4(4);
    }
    assert _len == 0 || _len < _ls.length:"_len = " + _len + ", _ls.length = " + _ls.length;
    assert _id == null || _id.length == _ls.length;
    assert _len <= _len2;
  }

  // Do any final actions on a completed NewVector.  Mostly: compress it, and
  // do a DKV put on an appropriate Key.  The original NewVector goes dead
  // (does not live on inside the K/V store).
  public Chunk new_close() {
    Chunk chk = compress();
    if(_vec instanceof AppendableVec)
      ((AppendableVec)_vec).closeChunk(this);
    return chk;
  }
  public void close(Futures fs) { close(_cidx,fs); }

  protected void set_enum(){
    for(int i = 0; i < _xs.length; ++i){
      if(_xs[i] == (Integer.MIN_VALUE+1))
        _xs[i] = 0;
      else
        setNA_impl2(i);
    }
  }
  protected void switch_to_doubles(){
    assert _ds == null;
    double [] ds = MemoryManager.malloc8d(_len);
    for(int i = 0; i < _len; ++i)
      if(isNA2(i) || isEnum2(i))ds[i] = Double.NaN;
      else  ds[i] = _ls[i]*DParseTask.pow10(_xs[i]);
    _ls = null;
    _xs = null;
    _ds = ds;
  }
  protected void set_sparse(int nzeros){
    if(_len == nzeros)return;
    assert _len == _len2:"_len = " + _len + ", _len2 = " + _len2 + ", nzeros = " + nzeros;
    int zs = 0;
    if(_ds == null){
      assert nzeros < _ls.length;
      _id = MemoryManager.malloc4(_ls.length);
      for(int i = 0; i < _len; ++i){
        if(_ls[i] == 0)++zs;
        else {
          _ls[i-zs] = _ls[i];
          _xs[i-zs] = _xs[i];
          _id[i-zs] = i;
        }
      }
    } else {
      assert nzeros < _ds.length;
      _id = MemoryManager.malloc4(_ds.length);
      for(int i = 0; i < _len; ++i){
        if(_ds[i] == 0)++zs;
        else {
          _ds[i-zs] = _ds[i];
          _id[i-zs] = i;
        }
      }
    }
    assert zs == (_len - nzeros);
    _len = nzeros;
  }
  protected void cancel_sparse(){
    if(_len != _len2){
      if(_ds == null){
        int []  xs = MemoryManager.malloc4(_len2);
        long [] ls = MemoryManager.malloc8(_len2);
        for(int i = 0; i < _len; ++i){
          xs[_id[i]] = _xs[i];
          ls[_id[i]] = _ls[i];
        }
        _xs = xs;
        _ls = ls;
      } else {
        double [] ds = MemoryManager.malloc8d(_len2);
        for(int i = 0; i < _len; ++i) ds[_id[i]] = _ds[i];
        _ds = ds;
      }
      _id = null;
      _len = _len2;
    }
  }
  // Study this NewVector and determine an appropriate compression scheme.
  // Return the data so compressed.
  static final int MAX_FLOAT_MANTISSA = 0x7FFFFF;
  Chunk compress() {
    // Check for basic mode info: all missing or all strings or mixed stuff
    byte mode = type();
    if( mode==AppendableVec.NA ) // ALL NAs, nothing to do
      return new C0DChunk(Double.NaN,_len);
    boolean rerun=false;
    if(mode == AppendableVec.ENUM){
      for( int i=0; i<_len; i++ )
        if(isEnum2(i))
          _xs[i] = 0;
        else if(!isNA2(i)){
          setNA_impl2(i);
          ++_naCnt;
        }
        // Smack any mismatched string/numbers
    } else if(mode == AppendableVec.NUMBER){
      for( int i=0; i<_len; i++ )
        if(isEnum2(i)) {
          setNA_impl2(i);
          rerun = true;
        }
    }
    if( rerun ) { _naCnt = -1;  type(); } // Re-run rollups after dropping all numbers/enums
    boolean sparse = false;
    // sparse? treat as sparse iff we have at least MIN_SPARSE_RATIOx more zeros than nonzeros
    if(MIN_SPARSE_RATIO*(_naCnt + _nzCnt) < _len2) {
      set_sparse(_naCnt + _nzCnt);
      sparse = true;
    }
    // If the data was set8 as doubles, we do a quick check to see if it's
    // plain longs.  If not, we give up and use doubles.
    if( _ds != null ) {
      int i=0;
      for( ; i<_len; i++ ) // Attempt to inject all doubles into longs
        if( !Double.isNaN(_ds[i]) && (double)(long)_ds[i] != _ds[i] ) break;
      if(i < _len)
        return sparse?new CXDChunk(_len2,_len,8,bufD(8)):chunkD();
      _ls = new long[_ds.length]; // Else flip to longs
      _xs = new int [_ds.length];
      double [] ds = _ds;
      _ds = null;
      for( i=0; i<_len; i++ )   // Inject all doubles into longs
        if( Double.isNaN(ds[i]) )setNA_impl2(i);
        else                     _ls[i] = (long)ds[i];
    }

    // IF (_len2 > _len) THEN Sparse
    // Check for compressed *during appends*.  Here we know:
    // - No specials; _xs[]==0.
    // - No floats; _ds==null
    // - NZ length in _len, actual length in _len2.
    // - Huge ratio between _len2 and _len, and we do NOT want to inflate to
    //   the larger size; we need to keep it all small all the time.
    // - Rows in _xs

    // Data in some fixed-point format, not doubles
    // See if we can sanely normalize all the data to the same fixed-point.
    int  xmin = Integer.MAX_VALUE;   // min exponent found
    long lemin= 0, lemax=lemin; // min/max at xmin fixed-point
    boolean overflow=false;
    boolean floatOverflow = false;
    boolean first = true;
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    int p10iLength = DParseTask.powers10i.length;

    for( int i=0; i<_len; i++ ) {
      if( isNA2(i) ) continue;
      long l = _ls[i];
      int  x = _xs[i];
      assert x != Integer.MIN_VALUE:"l = " + l + ", x = " + x;
      if( x==Integer.MIN_VALUE+1) x=0; // Replace enum flag with no scaling
      assert l!=0 || x==0:"l == 0 while x = " + x + " ls = " + Arrays.toString(_ls);      // Exponent of zero is always zero
      // Compute per-chunk min/max
      double d = l*DParseTask.pow10(x);
      if( d < min ) min = d;
      if( d > max ) max = d;
      long t;                   // Remove extra scaling
      while( l!=0 && (t=l/10)*10==l ) { l=t; x++; }
      floatOverflow = Math.abs(l) > MAX_FLOAT_MANTISSA;
      if( first ) {
        first = false;
        xmin = x;
        lemin = lemax = l;
        continue;
      }
      // Track largest/smallest values at xmin scale.  Note overflow.
      if( x < xmin ) {
        if( overflow || (overflow = ((xmin-x) >=p10iLength)) ) continue;
        lemin *= DParseTask.pow10i(xmin-x);
        lemax *= DParseTask.pow10i(xmin-x);
        xmin = x;               // Smaller xmin
      }
      // *this* value, as a long scaled at the smallest scale
      if( overflow || (overflow = ((x-xmin) >=p10iLength)) ) continue;
      long le = l*DParseTask.pow10i(x-xmin);
      if( le < lemin ) lemin=le;
      if( le > lemax ) lemax=le;
    }

    if(_len2 != _len){ // sparse? compare xmin/lemin/lemax with 0
      lemin = Math.min(0, lemin);
      lemax = Math.max(0, lemax);
      min = Math.min(min,0);
      max = Math.max(max,0);
    }

    // Constant column?
    if( _naCnt==0 && min==max ) {
      return ((long)min  == min)
          ? new C0LChunk((long)min,_len2)
          : new C0DChunk(      min,_len2);
    }

    // Boolean column?
    if (max == 1 && min == 0 && xmin == 0 && !overflow) {
      if(sparse) { // Very sparse?
        return  _naCnt==0
          ?new CX0Chunk(_len2,_len,bufS(0))// No NAs, can store as sparse bitvector
          :new CXIChunk(_len2,_len,1,bufS(1)); // have NAs, store as sparse 1byte values
      }

      int bpv = _strCnt+_naCnt > 0 ? 2 : 1;   // Bit-vector
      byte[] cbuf = bufB(bpv);
      return new CBSChunk(cbuf, cbuf[0], cbuf[1]);
    }

    final boolean fpoint = xmin < 0 || min < Long.MIN_VALUE || max > Long.MAX_VALUE;

    if(sparse){
      if(fpoint) return new CXDChunk(_len2,_len,8,bufD(8));
      int sz = 8;
      if(Short.MIN_VALUE <= min && max <= Short.MAX_VALUE)sz = 2;
      else if(Integer.MIN_VALUE <= min && max <= Integer.MAX_VALUE)sz = 4;
      return new CXIChunk(_len2,_len,sz,bufS(sz));
    }
    // Exponent scaling: replacing numbers like 1.3 with 13e-1.  '13' fits in a
    // byte and we scale the column by 0.1.  A set of numbers like
    // {1.2,23,0.34} then is normalized to always be represented with 2 digits
    // to the right: {1.20,23.00,0.34} and we scale by 100: {120,2300,34}.
    // This set fits in a 2-byte short.

    // We use exponent-scaling for bytes & shorts only; it's uncommon (and not
    // worth it) for larger numbers.  We need to get the exponents to be
    // uniform, so we scale up the largest lmax by the largest scale we need
    // and if that fits in a byte/short - then it's worth compressing.  Other
    // wise we just flip to a float or double representation.
    if( overflow || (fpoint && floatOverflow) || -35 > xmin || xmin > 35 )
      return chunkD();
    if( fpoint ) {
      if((int)lemin == lemin && (int)lemax == lemax){
        if(lemax-lemin < 255 && (int)lemin == lemin ) // Fits in scaled biased byte?
          return new C1SChunk( bufX(lemin,xmin,C1SChunk.OFF,0),(int)lemin,DParseTask.pow10(xmin));
        if(lemax-lemin < 65535 ) { // we use signed 2B short, add -32k to the bias!
          long bias = 32767 + lemin;
          return new C2SChunk( bufX(bias,xmin,C2SChunk.OFF,1),(int)bias,DParseTask.pow10(xmin));
        }
        if(lemax - lemin < Integer.MAX_VALUE)
          return new C4SChunk(bufX(lemin, xmin,C4SChunk.OFF,2),(int)lemin,DParseTask.pow10(xmin));
      }
      return chunkD();
    } // else an integer column
    // Compress column into a byte
    if(xmin == 0 &&  0<=lemin && lemax <= 255 && ((_naCnt + _strCnt)==0) )
      return new C1NChunk( bufX(0,0,C1NChunk.OFF,0));
    if(lemin < Integer.MIN_VALUE)return new C8Chunk( bufX(0,0,0,3));
    if( lemax-lemin < 255 ) {         // Span fits in a byte?
      if(0 <= min && max < 255 )      // Span fits in an unbiased byte?
        return new C1Chunk( bufX(0,0,C1Chunk.OFF,0));
      return new C1SChunk( bufX(lemin,xmin,C1SChunk.OFF,0),(int)lemin,DParseTask.pow10i(xmin));
    }

    // Compress column into a short
    if( lemax-lemin < 65535 ) {               // Span fits in a biased short?
      if( xmin == 0 && Short.MIN_VALUE < lemin && lemax <= Short.MAX_VALUE ) // Span fits in an unbiased short?
        return new C2Chunk( bufX(0,0,C2Chunk.OFF,1));
      int bias = (int)(lemin-(Short.MIN_VALUE+1));
      return new C2SChunk( bufX(bias,xmin,C2SChunk.OFF,1),bias,DParseTask.pow10i(xmin));
    }
    // Compress column into ints
    if( Integer.MIN_VALUE < min && max <= Integer.MAX_VALUE )
      return new C4Chunk( bufX(0,0,0,2));
    return new C8Chunk( bufX(0,0,0,3));
  }

  private static long [] NAS = {C1Chunk._NA,C2Chunk._NA,C4Chunk._NA,C8Chunk._NA};

  // Compute a sparse integer buffer
  private byte[] bufS(final int valsz){
    int log = 0;
    while((1 << log) < valsz)++log;
    assert valsz == 0 || (1 << log) == valsz;
    final int ridsz = _len2 >= 65535?4:2;
    final int elmsz = ridsz + valsz;
    int off = CXIChunk.OFF;
    byte [] buf = MemoryManager.malloc1(off + _len*elmsz);
    for( int i=0; i<_len; i++, off += elmsz ) {
      if(ridsz == 2)
        UDP.set2(buf,off,(short)_id[i]);
      else
        UDP.set4(buf,off,_id[i]);
      if(valsz == 0){
        assert _xs[i] == 0 && _ls[i] == 1;
        continue;
      }
      assert _xs[i] == Integer.MIN_VALUE || _xs[i] >= 0:"unexpected exponent " + _xs[i]; // assert we have int or NA
      final long lval = _xs[i] == Integer.MIN_VALUE?NAS[log]:_ls[i]*DParseTask.pow10i(_xs[i]);
      switch(valsz){
        case 1:
          buf[off+ridsz] = (byte)lval;
          break;
        case 2:
          short sval = (short)lval;
          UDP.set2(buf,off+ridsz,sval);
          break;
        case 4:
          int ival = (int)lval;
          UDP.set4(buf, off+ridsz, ival);
          break;
        case 8:
          UDP.set8(buf, off+ridsz, lval);
          break;
        default:
          throw H2O.unimpl();
      }
    }
    assert off==buf.length;
    return buf;
  }

  // Compute a sparse float buffer
  private byte[] bufD(final int valsz){
    int log = 0;
    while((1 << log) < valsz)++log;
    assert (1 << log) == valsz;
    final int ridsz = _len2 >= 65535?4:2;
    final int elmsz = ridsz + valsz;
    int off = CXDChunk.OFF;
    byte [] buf = MemoryManager.malloc1(off + _len*elmsz);
    for( int i=0; i<_len; i++, off += elmsz ) {
      if(ridsz == 2)
        UDP.set2(buf,off,(short)_id[i]);
      else
        UDP.set4(buf,off,_id[i]);
      final double dval = _ds == null?isNA2(i)?Double.NaN:_ls[i]*DParseTask.pow10(_xs[i]):_ds[i];
      switch(valsz){
        case 4:
          UDP.set4f(buf, off + ridsz, (float) dval);
          break;
        case 8:
          UDP.set8d(buf, off + ridsz, dval);
          break;
        default:
          throw H2O.unimpl();
      }
    }
    assert off==buf.length;
    return buf;
  }
  // Compute a compressed integer buffer
  private byte[] bufX( long bias, int scale, int off, int log ) {
    byte[] bs = new byte[(_len2<<log)+off];
    int j = 0;
    for( int i=0; i<_len2; i++ ) {
      long le = -bias;
      if(_id == null || (j < _id.length && _id[j] == i)){
        if( isNA2(j) ) {
          le = NAS[log];
        } else {
          int x = (_xs[j]==Integer.MIN_VALUE+1 ? 0 : _xs[j])-scale;
          le += x >= 0
              ? _ls[j]*DParseTask.pow10i( x)
              : _ls[j]/DParseTask.pow10i(-x);
        }
        ++j;
      }
      switch( log ) {
        case 0:          bs [i    +off] = (byte)le ; break;
        case 1: UDP.set2(bs,(i<<1)+off,  (short)le); break;
        case 2: UDP.set4(bs,(i<<2)+off,    (int)le); break;
        case 3: UDP.set8(bs,(i<<3)+off,         le); break;
        default: H2O.fail();
      }
    }
    assert j == _len:"j = " + j + ", len = " + _len + ", len2 = " + _len2 + ", id[j] = " + _id[j];
    return bs;
  }

  // Compute a compressed double buffer
  private Chunk chunkD() {
    final byte [] bs = MemoryManager.malloc1(_len2*8);
    int j = 0;
    for(int i = 0; i < _len2; ++i){
      double d = 0;
      if(_id == null || _id[j] == i){
        d = _ds != null?_ds[j]:(isNA2(j)||isEnum(j))?Double.NaN:_ls[j]*DParseTask.pow10(_xs[j]);
        ++j;
      }
      UDP.set8d(bs, 8*i, d);
    }
    assert j == _len:"j = " + j + ", _len = " + _len;
    return new C8DChunk(bs);
  }

  // Compute compressed boolean buffer
  private byte[] bufB(int bpv) {
    assert bpv == 1 || bpv == 2 : "Only bit vectors with/without NA are supported";
    final int off = CBSChunk.OFF;
    int clen  = off + CBSChunk.clen(_len2, bpv);
    byte bs[] = new byte[clen];
    // Save the gap = number of unfilled bits and bpv value
    bs[0] = (byte) (((_len2*bpv)&7)==0 ? 0 : (8-((_len2*bpv)&7)));
    bs[1] = (byte) bpv;

    // Dense bitvector
    int  boff = 0;
    byte b    = 0;
    int  idx  = CBSChunk.OFF;
    int j = 0;
    for (int i=0; i<_len2; i++) {
      byte val = 0;
      if(_id == null || (j < _id.length && _id[j] == i)) {
        assert bpv == 2 || !isNA2(j);
        val = (byte)(isNA2(j)?CBSChunk._NA:_ls[j]);
        ++j;
      }
      if( bpv==1 )
        b = CBSChunk.write1b(b, val, boff);
      else
        b = CBSChunk.write2b(b, val, boff);
      boff += bpv;
      if (boff>8-bpv) { assert boff == 8; bs[idx] = b; boff = 0; b = 0; idx++; }
    }
    assert j == _len;
    assert bs[0] == (byte) (boff == 0 ? 0 : 8-boff):"b[0] = " + bs[0] + ", boff = " + boff + ", bpv = " + bpv;
    // Flush last byte
    if (boff>0) bs[idx++] = b;
    return bs;
  }

  // Set & At on NewChunks are weird: only used after inflating some other
  // chunk.  At this point the NewChunk is full size, no more appends allowed,
  // and the xs exponent array should be only full of zeros.  Accesses must be
  // in-range and refer to the inflated values of the original Chunk.
  @Override boolean set_impl(int i, long l) {
    if( _ds   != null ) return set_impl(i,(double)l);
    if(_len != _len2){ // sparse?
      int idx = Arrays.binarySearch(_id,0,_len,i);
      if(idx >= 0)i = idx;
      else cancel_sparse(); // for now don't bother setting the sparse value
    }
    _ls[i]=l; _xs[i]=0;
    return true;
  }

  @Override public boolean set_impl(int i, double d) {
    if(_ds == null){
      assert _len == 0 || _ls != null;
      switch_to_doubles();
    }
    if(_len != _len2){ // sparse?
      int idx = Arrays.binarySearch(_id,0,_len,i);
      if(idx >= 0)i = idx;
      else cancel_sparse(); // for now don't bother setting the sparse value
    }
    while(i >= _len2) append2slowd();
    _ds[i] = d;
    return true;
  }
  @Override boolean set_impl(int i, float f) {  return set_impl(i,(double)f); }

  protected final boolean setNA_impl2(int i) {
    if( isNA2(i) ) return true;
    if( _ls != null ) { _ls[i] = Long.MAX_VALUE; _xs[i] = Integer.MIN_VALUE; }
    if( _ds != null ) { _ds[i] = Double.NaN; }
    return true;
  }
  @Override boolean setNA_impl(int i) {
    if( isNA_impl(i) ) return true;
    if(_len != _len2){
      int idx = Arrays.binarySearch(_id,0,_len,i);
      if(idx >= 0) i = idx;
      else cancel_sparse(); // todo - do not necessarily cancel sparse here
    }
    return setNA_impl2(i);
  }
  @Override public long   at8_impl( int i ) {
    if( _len2 != _len ) {
      int idx = Arrays.binarySearch(_id,0,_len,i);
      if(idx >= 0) i = idx;
      else return 0;
    }
    if( _ls == null ) return (long)_ds[i];
    return _ls[i]*DParseTask.pow10i(_xs[i]);
  }
  @Override public double atd_impl( int i ) {
    if( _len2 != _len ) {
      int idx = Arrays.binarySearch(_id,0,_len,i);
      if(idx >= 0) i = idx;
      else return 0;
    }
    if( _ds == null ) return at8_impl(i);
    assert _xs==null; return _ds[i];
  }
  @Override public boolean isNA_impl( int i ) {
    if( _len2 != _len ) {
      int idx = Arrays.binarySearch(_id,0,_len,i);
      if(idx >= 0) i = idx;
      else return false;
    }
    return isNA2(i);
  }
  @Override public AutoBuffer write(AutoBuffer bb) { throw H2O.fail(); }
  @Override public NewChunk read(AutoBuffer bb) { throw H2O.fail(); }
  @Override NewChunk inflate_impl(NewChunk nc) { throw H2O.fail(); }
  @Override boolean hasFloat() { throw H2O.fail(); }
  @Override public String toString() { return "NewChunk._len="+_len; }
}
