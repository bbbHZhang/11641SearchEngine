import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *  The NEAR operator for all retrieval models.
 */
public class QryIopNear extends QryIop {
    private final int n;
    public QryIopNear(int n) {
        super();
        this.n = n;
    }

    /**
     *  Evaluate the query operator; the result is an internal inverted
     *  list that may be accessed via the internal iterators.
     *  @throws IOException Error accessing the Lucene index.
     */
    protected void evaluate () throws IOException {

        //  Create an empty inverted list.  If there are no query arguments,
        //  that's the final result.

        this.invertedList = new InvList (this.getField());

        if (args.size () == 0) {
            return;
        }

        while(true){
            //ecah round add one doc id
            if(!this.docIteratorHasMatchAll(null)){
                break;
            }

            QryIop qi0 = (QryIop)this.args.get(0);
            int docId = qi0.docIteratorGetMatch();

            if(docId == Qry.INVALID_DOCID) break;


            //now get a doc id matches all queries
            //then go deep into each pair of two doc's postings
            List<Integer> prev = ((QryIop)this.args.get(0)).docIteratorGetMatchPosting().positions;

            for(int i = 1; i < this.args.size(); i++){
//                QryIop qii = (QryIop)this.args.get(i);
                List<Integer> curr = ((QryIop)this.args.get(i)).docIteratorGetMatchPosting().positions;
                List<Integer> newprev = new ArrayList<>();
                int ptr_prev = 0;
                int ptr_curr = 0;
                while(ptr_prev < prev.size() && ptr_curr < curr.size()){
                    if(prev.get(ptr_prev) > curr.get(ptr_curr)){//prev > curr
                        ptr_curr++;
                        continue;
                    } else if (curr.get(ptr_curr) - prev.get(ptr_prev) <= n){//n => curr - prev >0
                        newprev.add(curr.get(ptr_curr));
                        ptr_prev++;
                        ptr_curr++;
                        continue;
                    } else {//prev < curr - n
                        ptr_prev++;
                        continue;
                    }
                }
                prev = newprev;
            }

            if(prev.size() > 0){
                Collections.sort (prev);
                this.invertedList.appendPosting(docId, prev);
            }

            for(Qry qi: this.args){
                qi.docIteratorAdvancePast(docId);
            }
        }


    }

}
