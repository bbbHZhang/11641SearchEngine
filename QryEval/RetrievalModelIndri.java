/**
 *  An object that stores parameters for the Indri
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelIndri extends RetrievalModel {
    float mu;
    float lambda;
    public RetrievalModelIndri(float mu, float lambda){
        this.mu = mu;
        this.lambda = lambda;
    }

    //The Indri retrieval method must support the Indri AND, WAND, WSUM, and WINDOW query operator,
    // as well as the SYN and NEAR/n query operators.
    // The AND query operator is Indri's default query operator for unstructured (bag of words) queries,
    // but note that the score calculation will not be the same as your Ranked Boolean AND.
    public String defaultQrySopName () {
        return new String ("#and");
    }


}
