package water.api;

import static water.util.ModelUtils.getPredictions;
import org.junit.Assert;
import org.junit.Test;
import water.MRTask2;
import water.Request2;
import water.UKV;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Utils;

import java.util.Arrays;
import java.util.Random;

public class HitRatio extends Request2 {
  static final int API_WEAVER = 1; // This file has auto-gen'd doc & json fields
  static public DocGen.FieldDoc[] DOC_FIELDS; // Initialized from Auto-Gen code.
  public static final String DOC_GET = "Hit Ratio";

  @API(help = "", required = true, filter = Default.class, json=true)
  public Frame actual;

  @API(help="Column of the actual results (will display vertically)", required=true, filter=actualVecSelect.class, json=true)
  public Vec vactual;
  class actualVecSelect extends VecClassSelect { actualVecSelect() { super("actual"); } }

  @API(help = "", required = true, filter = Default.class, json=true)
  public Frame predict;

  @API(help = "Max. number of labels (K) to use for hit ratio computation", required = false, filter = Default.class, json = true)
  private int max_k = 10;
  public void set_max_k(int k) { max_k = k; }

  @API(help = "Random number seed for breaking ties between equal probabilities", required = false, filter = Default.class, json = true)
  private long seed = new Random().nextLong();

  @API(help="domain of the actual response")
  private String [] actual_domain;

  @API(help="Hit ratios for k=1...K")
  private float[] hit_ratios;
//  public float[] hit_ratios() { return hit_ratios; }

  public HitRatio() {}

  @Override public Response serve() {
    Vec va = null, vp;
    // Input handling
    if( actual==null || predict==null )
      throw new IllegalArgumentException("Missing actual or predict!");
    if( vactual==null )
      throw new IllegalArgumentException("Missing vactual!");
    if (vactual.length() != predict.anyVec().length())
      throw new IllegalArgumentException("Both arguments must have the same length!");
    if (!vactual.isInt())
      throw new IllegalArgumentException("Actual column must be integer class labels!");

    try {
      va = vactual.toEnum(); // always returns TransfVec
      actual_domain = va._domain;

      if (max_k > predict.numCols()-1)
        throw new IllegalArgumentException("K cannot be larger than " + String.format("%,d", predict.numCols()-1));
      final Frame actual_predict = new Frame(predict.names(), predict.vecs());
      actual_predict.replace(0, va); // place actual labels in first column
      hit_ratios = new HitRatioTask(max_k, seed).doAll(actual_predict).hit_ratios();

      return Response.done(this);
    } catch( Throwable t ) {
      return Response.error(t);
    } finally {       // Delete adaptation vectors
      if (va!=null) UKV.remove(va._key);
    }
  }

  @Override public boolean toHTML( StringBuilder sb ) {
    if (hit_ratios==null) return false;
    sb.append("<div>");
    DocGen.HTML.section(sb, "Hit Ratio for Multi-Class Classification");
    DocGen.HTML.paragraph(sb, "(Frequency of actual class label to be among the top-K predicted class labels)");

    DocGen.HTML.arrayHead(sb);
    sb.append("<th>K</th>");
    sb.append("<th>Hit Ratio</th>");
    for (int k = 1; k<=max_k; ++k) sb.append("<tr><td>" + k + "</td><td>" + String.format("%.3f", hit_ratios[k-1]*100.) + "%</td></tr>");
    DocGen.HTML.arrayTail(sb);
    return true;
  }

  public void toASCII( StringBuilder sb ) {
    if (hit_ratios==null) return;
    sb.append("K   Hit-ratio\n");
    for (int k = 1; k<=max_k; ++k) sb.append(k + "   " + String.format("%.3f", hit_ratios[k-1]*100.) + "%\n");
  }

  /**
   * Update hit counts for given set of actual label and predicted labels
   * This is to be called for each predicted row
   * @param hits Array of length K, counting the number of hits (entries will be incremented)
   * @param actual_label 1 actual label
   * @param pred_labels K predicted labels
   */
  private static void updateHits(long[] hits, int actual_label, int[] pred_labels) {
    assert(hits != null);
    for (long h : hits) assert(h >= 0);
    assert(pred_labels != null);
    assert(actual_label >= 0);
    assert(hits.length == pred_labels.length);

    //find the first occurrence of the actual label and increment all counters from there on
    //do nothing if no hit
    for (int k=0;k<pred_labels.length;++k) {
      if (pred_labels[k] == actual_label) {
        while (k<pred_labels.length) hits[k++]++;
      }
    }
  }

  @Test
  public void testHits() {
    long[] hits = new long[4];
    int actual_label = 3; int[] pred_labels = {0,3,2,1}; updateHits(hits, actual_label, pred_labels);
    Assert.assertTrue(hits[0] == 0);
    Assert.assertTrue(hits[1] == 1);
    Assert.assertTrue(hits[2] == 1);
    Assert.assertTrue(hits[3] == 1);
    actual_label = 0; pred_labels = new int[]{1,2,3,0}; updateHits(hits, actual_label, pred_labels);
    Assert.assertTrue(hits[0] == 0+0);
    Assert.assertTrue(hits[1] == 1+0);
    Assert.assertTrue(hits[2] == 1+0);
    Assert.assertTrue(hits[3] == 1+1);
    actual_label = 1; pred_labels = new int[]{0,3,1,2}; updateHits(hits, actual_label, pred_labels);
    Assert.assertTrue(hits[0] == 0+0+0);
    Assert.assertTrue(hits[1] == 1+0+0);
    Assert.assertTrue(hits[2] == 1+0+1);
    Assert.assertTrue(hits[3] == 1+1+1);
  }


  // Compute CMs for different thresholds via MRTask2
  private static class HitRatioTask extends MRTask2<HitRatioTask> {
    /* @OUT CMs */ public final float[] hit_ratios() {
      float[] hit_ratio = new float[_K];
      if (_count == 0) return new float[_K];
      for (int i=0;i<_K;++i) {
        hit_ratio[i] = ((float)_hits[i])/_count;
      }
      return hit_ratio;
    }

    /* IN K */
    final private int _K;
    /* IN Seed */
    private long _seed;

    /* Helper */
    private long[] _hits; //the number of hits, length: K
    private long _count; //the number of scored rows

    HitRatioTask(int K, long seed) {
      _K = K;
      _seed = seed;
    }

    @Override public void map( Chunk[] cs ) {
      _hits = new long[_K];
      Arrays.fill(_hits, 0);

      // pseudo-random tie breaking needs some bits to work with
      final double[] tieBreaker = new double [] {
              new Random(_seed).nextDouble(), new Random(_seed+1).nextDouble(),
              new Random(_seed+2).nextDouble(), new Random(_seed+3).nextDouble() };

      float [] preds = new float[cs.length];

      // rows
      for( int r=0; r < cs[0]._len; r++ ) {
        if (cs[0].isNA0(r)) {
          _count--;
          continue;
        }
        final int actual_label = (int)cs[0].at80(r);

        //predict K labels
        for(int p=1; p < cs.length; p++) preds[p] = (float)cs[p].at0(r);
        final int[] pred_labels = getPredictions(_K, preds, tieBreaker);

        if (actual_label < cs.length-1) updateHits(_hits, actual_label, pred_labels);
      }
      _count += cs[0]._len;
    }

    @Override public void reduce( HitRatioTask other ) {
      assert(other._K == _K);
      _hits = Utils.add(_hits, other._hits);
      _count += other._count;
    }
  }
}
