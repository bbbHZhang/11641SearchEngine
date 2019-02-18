import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch (RetrievalModel r) {
        if(r instanceof RetrievalModelIndri) return this.docIteratorHasMatchMin(r);
        return this.docIteratorHasMatchAll (r);
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore (RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelUnrankedBoolean) {
            return this.getScoreUnrankedBoolean (r);
        } else if(r instanceof  RetrievalModelRankedBoolean ) {
            return this.getScoreRankedBoolean(r);
        } else if(r instanceof  RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the OR operator.");
        }
    }

    @Override
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        double res = 1.0;
        for(Qry q: this.args){
            res *= ((QrySop) q).getDefaultScore(r, docid);
        }

        return Math.pow(res, 1/(double)this.args.size());
    }

    double getScoreIndri(RetrievalModel r)throws IOException {
//        if (! this.docIteratorHasMatchCache()) {
//            //?????????????????????????????????????????not match what>>>>>>>>>>
//            //no document has this term
//            return 0.0;
//        } else {
            double res = 1.0;
            int docid = this.docIteratorGetMatch();
            double weight = 1/(double) this.args.size();
            for (Qry q : this.args) {
                if(q.docIteratorHasMatch(r) && q.docIteratorGetMatch() == docid){
                    double score = Math.pow(((QrySop) q).getScore(r), weight);
                    res *= score;
                }else{
                    double score = Math.pow(((QrySop) q).getDefaultScore(r, docid), weight);
                    res *= score;
                }
//                System.out.println("res " + res);

            }
            return res;
        }
//    }

    /**
     *  getScore for the UnrankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    double getScoreRankedBoolean(RetrievalModel r) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            double res = Double.MAX_VALUE;
            for(Qry q: this.args){
                double qScore = ((QrySop) q).getScore (r);
                res = Math.min(res, qScore);
            }
            return res;
        }
    }

    /**
     *  getScore for the RankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return 1.0;
        }
    }

}
