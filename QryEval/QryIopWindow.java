import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QryIopWindow extends QryIop {
    private final int distance;

    public QryIopWindow(int distance) {
        this.distance = distance;
    }

    @Override
    protected void evaluate() throws IOException {
        //window's parameter are inverted lists

        this.invertedList = new InvList(this.getField());

        if (args.size() == 0) {
            return;
        }

        while (true) {
            //each round in this loop will add a doc into the inverted list
            if (!this.docIteratorHasMatchAll(null)) {
                break;
            }

            QryIop qi0 = (QryIop) this.args.get(0);
            int docId = qi0.docIteratorGetMatch();

            if (docId == Qry.INVALID_DOCID) break;

            ArrayList<Integer> locForThisDoc = new ArrayList<>();

            //go into each parameter's loc posting
            //check whether any of them reach the end
            //
            boolean allParaHasMatch = true;
            List<Integer> locations = new ArrayList<>();
            int[] location = new int[this.args.size()];
            while(allParaHasMatch){
                int min = Integer.MAX_VALUE;
                int max = -1;
                for(int i = 0; i < this.args.size(); i++){
                    QryIop qi = (QryIop)this.args.get(i);
                    if(!qi.locIteratorHasMatch()){
                        allParaHasMatch = false;
                        break;
                    }else{
                        location[i] = qi.locIteratorGetMatch();
                        if(min == Integer.MAX_VALUE || qi.locIteratorGetMatch() < location[min]) min = i;
                        if(max == -1 || qi.locIteratorGetMatch() > location[max]) max = i;
                    }
                }
                if(!allParaHasMatch) break;
                //know every one has a match locid
                if(location[max] - location[min] < this.distance){
                    //if match, add max and advance all to next one
                    locForThisDoc.add(location[max]);
                    for(Qry q: this.args){
                        ((QryIop)q).locIteratorAdvance();
                    }
                } else {
                    //otherwise, advance min and redo everything
                    ((QryIop)this.args.get(min)).locIteratorAdvance();
                }
            }
            if (locForThisDoc.size() > 0) {
                Collections.sort(locForThisDoc);
                this.invertedList.appendPosting(docId, locForThisDoc);
            }

            for (Qry qi : this.args) {
                qi.docIteratorAdvancePast(docId);
            }



        }


//            //now get a doc id matches all queries
//            //then go deep into each pair of two doc's postings
//            List<Integer> prev = ((QryIop) this.args.get(0)).docIteratorGetMatchPosting().positions;
//
//            for (int i = 1; i < this.args.size(); i++) {
////                QryIop qii = (QryIop)this.args.get(i);
//                List<Integer> curr = ((QryIop) this.args.get(i)).docIteratorGetMatchPosting().positions;
//                List<Integer> newprev = new ArrayList<>();
//                int ptr_prev = 0;
//                int ptr_curr = 0;
//                while (ptr_prev < prev.size() && ptr_curr < curr.size()) {
//                    if (Math.abs(ptr_curr - ptr_prev) <= distance) {
//                        newprev.add(Math.max(ptr_curr, ptr_prev));
//                        ptr_curr++;
//                        ptr_prev++;
//                    } else {
//                        if (ptr_curr < ptr_prev) {
//                            ptr_curr++;
//                        } else {
//                            ptr_prev++;
//                        }
//                    }
//                }
//                prev = newprev;
//            }
//
//            if (prev.size() > 0) {
//                Collections.sort(prev);
//                this.invertedList.appendPosting(docId, prev);
//            }
//
//            for (Qry qi : this.args) {
//                qi.docIteratorAdvancePast(docId);
//            }
//        }
    }
}
