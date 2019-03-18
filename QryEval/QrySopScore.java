/**
 *  Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {


  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean(r);
    } else if (r instanceof RetrievalModelRankedBoolean) {
      return this.getScoreRankedBoolean(r);
    } else if (r instanceof RetrievalModelBM25){
      return this.getScoreBM25(r);
    } else if(r instanceof RetrievalModelIndri){
      return this.getScoreIndri(r);
    }else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }

  /**
   *  getScore for the BM25 model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreBM25 (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      QryIop curr = (QryIop)this.args.get(0);
      RetrievalModelBM25 bm25 = (RetrievalModelBM25) r;
      double qtf = 1.0;
      //Score will only have one arg
      double RSJweight = Math.max(0.0, Math.log((Idx.getNumDocs() - curr.getDf() + 0.5)/(curr.getDf() + 0.5)));
      double avg_doclen = Idx.getSumOfFieldLengths(curr.field)/(double)Idx.getDocCount(curr.field);
      double score = (double)curr.docIteratorGetMatchPosting().tf/
              (curr.docIteratorGetMatchPosting().tf + bm25.k_1*(
                      (1-bm25.b) + bm25.b * Idx.getFieldLength(curr.field, curr.docIteratorGetMatch())/avg_doclen));
//      System.out.println(score);
      return RSJweight * score * (bm25.k_3 + 1)*qtf/(qtf + bm25.k_3);
    }
  }

  /**
   *  getScore for the Indri model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreIndri (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      //= 0.0?????????????????????????????????????
      return 1.0;
    } else {
      RetrievalModelIndri indri = (RetrievalModelIndri) r;
      QryIop curr = (QryIop)this.args.get(0);
      double tf = curr.docIteratorGetMatchPosting().tf;
      double ctf = curr.getCtf();
      long lengthc = Idx.getSumOfFieldLengths(curr.field);
      long lengthd = Idx.getFieldLength(curr.field, this.docIteratorGetMatch());
      double part1 = (1-indri.lambda) * (tf + indri.mu * ctf/lengthc) / (lengthd + indri.mu);
      double part2 = indri.lambda * ctf / lengthc;
      return part1 + part2;
    }
  }
  @Override
  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
    //who will call this method?????????????????????//
    if(r instanceof RetrievalModelIndri){
      double lambda = ((RetrievalModelIndri) r).lambda;
      double mu = ((RetrievalModelIndri) r).mu;
      QryIop q = (QryIop)this.args.get(0);

      double ctf = q.getCtf();
      double Pmle = 0.0;
      double lengthc = Idx.getSumOfFieldLengths(q.field);
      if(ctf == 0){
        ctf = 0.5;
      }
      Pmle = ctf/lengthc;
      double lengthdoc = Idx.getFieldLength(q.field, (int)docid);
      return (1-lambda)* mu * Pmle/(lengthdoc + mu) + lambda * Pmle;
    }
    return 0.0;
  }
  /**
   *  getScore for the Ranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      //Score will only have one arg
      return ((QryIop)this.args.get(0)).docIteratorGetMatchPosting().tf;
    }
  }
  
  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {

      return 0.0;
    } else {
      return 1.0;
    }
  }

  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {

    Qry q = this.args.get (0);
    q.initialize (r);
  }

}
