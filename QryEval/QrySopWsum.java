import java.io.IOException;
import java.util.ArrayList;

public class QrySopWsum extends QrySop {
    ArrayList<Double> args_weight = new ArrayList<>();

    @Override
    public double getScore(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException(r.getClass().getName() + " doesn't support the SUM operator.");
        }
    }

    private double getScoreIndri(RetrievalModel r) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            int docID = this.docIteratorGetMatch();
            double res = 0.0;

            double weightSum = 0.0;
            for(Double d : args_weight){
                weightSum += d;
            }

            for(int i = 0; i < this.args.size(); i++){
                QrySop qs = (QrySop) this.args.get(i);
                double qScore = 0.0;
                if(qs.docIteratorHasMatch(r) && qs.docIteratorGetMatch() == docID){
                    qScore = qs.getScore(r);
                } else {
                    qScore = qs.getDefaultScore(r, docID);
                }
                res += qScore * args_weight.get(i) / weightSum;
            }
            return res;

        }
    }

    @Override
    double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        if(r instanceof  RetrievalModelIndri){
            double res = 0.0;

            double weightSum = 0.0;
            for(Double d : args_weight){
                weightSum += d;
            }

            for(int i = 0; i < this.args.size(); i++){
                QrySop qs = (QrySop) this.args.get(i);
                double qScore = qs.getDefaultScore(r, docid);
                res += qScore * args_weight.get(i) / weightSum;
            }
            return res;
        }
        return 0;
    }

    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        if(r instanceof  RetrievalModelIndri){
            return this.docIteratorHasMatchMin(r);
        }
        return this.docIteratorHasMatchAll(r);
    }
}
