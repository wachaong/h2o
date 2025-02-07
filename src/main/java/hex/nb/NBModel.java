package hex.nb;

import hex.FrameTask.DataInfo;
import hex.nb.NaiveBayes.NBTask;
import water.Key;
import water.Model;
import water.api.*;
import water.api.Request.API;
import water.api.Request.Default;
import water.api.RequestBuilders.ElementBuilder;

public class NBModel extends Model {
  static final int API_WEAVER = 1; // This file has auto-gen'd doc & json fields
  static public DocGen.FieldDoc[] DOC_FIELDS; // Initialized from Auto-Gen code.

  @API(help = "Class distribution of the dependent variable")
  final double[] pprior;

  @API(help = "For every predictor variable, a table giving, for each attribute level, the conditional probabilities given the target class")
  final double[][][] pcond;

  @API(help = "Laplace smoothing parameter", required = true, filter = Default.class, lmin = 0, lmax = 100000, json = true)
  final double laplace;

  public NBModel(Key selfKey, Key dataKey, DataInfo dinfo, NBTask tsk, double[] pprior, double[][][] pcond, double laplace) {
    super(selfKey, dataKey, dinfo._adaptedFrame);
    this.pprior = pprior;
    this.pcond = pcond;
    this.laplace = laplace;
  }

  public double[] pprior() { return pprior; }
  public double[][][] pcond() { return pcond; }

  // Note: For small probabilities, product may end up zero due to underflow error. Can circumvent by taking logs.
  @Override protected float[] score0(double[] data, float[] preds) {
    double denom = 0;
    assert preds.length == (pprior.length + 1);   // Note: First column of preds is predicted response class

    // Compute joint probability of predictors for every response class
    for(int rlevel = 0; rlevel < pprior.length; rlevel++) {
      double num = 1;
      for(int col = 0; col < data.length; col++) {
        if(Double.isNaN(data[col])) continue;   // Skip predictor in joint x_1,...,x_m if NA
        int plevel = (int)data[col];
        num *= pcond[col][rlevel][plevel];    // p(x|y) = \Pi_{j = 1}^m p(x_j|y)
      }
      num *= pprior[rlevel];    // p(x,y) = p(x|y)*p(y)
      denom += num;             // p(x) = \Sum_{levels of y} p(x,y)
      preds[rlevel+1] = (float)num;
    }

    // Select class with highest conditional probability
    float max = -1;
    for(int i = 1; i < preds.length; i++) {
      preds[i] /= denom;    // p(y|x) = p(x,y)/p(x)

      if(preds[i] > max) {
        max = preds[i];
        preds[0] = i-1;
      }
    }
    return preds;
  }

  @Override public void delete() { super.delete(); }

  @Override public String toString(){
    StringBuilder sb = new StringBuilder("Naive Bayes Model (key=" + _key + " , trained on " + _dataKey + "):\n");
    return sb.toString();
  }

  public void generateHTML(String title, StringBuilder sb) {
    if(title != null && !title.isEmpty()) DocGen.HTML.title(sb, title);
    DocGen.HTML.paragraph(sb, "Model Key: " + _key);
    sb.append("<div class='alert'>Actions: " + Predict.link(_key, "Predict on dataset") + ", "
        + NaiveBayes.link(_dataKey, "Compute new model") + "</div>");

    DocGen.HTML.section(sb, "A-Priori Probabilities");
    sb.append("<span style='display: inline-block;'>");
    sb.append("<table class='table table-striped table-bordered'>");

    // Domain of the response variable
    String[] resdom = _domains[_domains.length-1];
    sb.append("<tr>");
    for(int i = 0; i < resdom.length; i++)
      sb.append("<th>").append(resdom[i]).append("</th>");
    sb.append("</tr>");

    // Display table of a-priori response probabilities
    sb.append("<tr>");
    for(int i = 0; i < pprior.length; i++)
      sb.append("<td>").append(ElementBuilder.format(pprior[i])).append("</td>");
    sb.append("</tr>");
    sb.append("</table></span>");

    DocGen.HTML.section(sb, "Conditional Probabilities");
    for(int col = 0; col < pcond.length; col++) {
      DocGen.HTML.paragraph(sb, "Column: " + _names[col]);
      sb.append("<span style='display: inline-block;'>");
      sb.append("<table class='table table-striped table-bordered'>");

      // Domain of the predictor variable
      sb.append("<tr>");
      sb.append("<th>").append("Response/Predictor").append("</th>");
      for(int i = 0; i < _domains[col].length; i++)
        sb.append("<th>").append(_domains[col][i]).append("</th>");
      sb.append("</tr>");

      // For each predictor, display table of conditional probabilities
      for(int r = 0; r < pcond[col].length; r++) {
        sb.append("<tr>");
        sb.append("<th>").append(resdom[r]).append("</th>");

        for(int c = 0; c < pcond[col][r].length; c++) {
          double e = pcond[col][r][c];
          sb.append("<td>").append(ElementBuilder.format(e)).append("</td>");
        }
        sb.append("</tr>");
      }
      sb.append("</table></span>");
    }
  }
}
