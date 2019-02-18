import java.io.IOException;

public class QrySopSum extends QrySop {

    //the arguments of SUM could only be: #SCORE type, score lists
    @Override
    public double getScore(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25(r);
        } else {
            throw new IllegalArgumentException(r.getClass().getName() + " doesn't support the SUM operator.");
        }
    }

    private double getScoreBM25(RetrievalModel r) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            int docID = this.docIteratorGetMatch();
            double res = 0.0;

            for(Qry q: this.args){
                QrySop qs = (QrySop) q;
                if(qs.docIteratorHasMatch(r) && q.docIteratorGetMatch() == docID){
                    res += ((QrySop) q).getScore(r);
                }
            }
            return res;
        }
    }

    @Override
    double getDefaultScore(RetrievalModel r, long docid) {
        return 0;
    }

    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
    }
}
