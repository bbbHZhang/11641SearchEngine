import java.io.IOException;
import java.util.ArrayList;

public class QrySopWand extends QrySop {
    ArrayList<Double> args_weight = new ArrayList<>();

    @Override
    public double getScore(RetrievalModel r) throws IOException {
        if (!(r instanceof RetrievalModelIndri)) {
            throw new IllegalArgumentException("Not support the WAND operator.");
        }
        double res = 1.0;

        int docid = this.docIteratorGetMatch();
        double weightSum = 0.0;
        for(Double d : this.args_weight){
            weightSum += d;
        }

        for (int i = 0; i < this.args.size(); i++) {
            Qry q = this.args.get(i);
            double weight = args_weight.get(i) / weightSum;
            double score;
            if(q.docIteratorHasMatch(r) && q.docIteratorGetMatch() == docid){
                score = Math.pow(((QrySop) q).getScore(r), weight);
            }else{
                score = Math.pow(((QrySop) q).getDefaultScore(r, docid), weight);
            }
            res *= score;
        }
        return res;
    }

    @Override
    double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        double weightSum = 0.0;
        for(Double d : this.args_weight){
            weightSum += d;
        }

        double res = 1.0;
        for (int i = 0; i < this.args.size(); i++) {
            double weight = args_weight.get(i) / weightSum;
            res *= Math.pow(((QrySop) this.args.get(i)).getDefaultScore(r, docid), weight);;
        }
        return res;
    }

    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        if(r instanceof RetrievalModelIndri) return this.docIteratorHasMatchMin(r);
        return this.docIteratorHasMatchAll (r);
    }
}
