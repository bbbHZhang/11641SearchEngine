import java.util.Map;

public class RetrievalModelLetor extends RetrievalModel {
    /**
     * letor:trainingQueryFile= A file of training queries.
     * letor:trainingQrelsFile= A file of relevance judgments. Column 1 is the query id. Column 2 is ignored. Column 3 is the document id. Column 4 indicates the degree of relevance (0-2).
     * letor:trainingFeatureVectorsFile= The file of feature vectors that your software will write for the training queries.
     * letor:featureDisable= A comma-separated list of features to disable for this experiment. For example, "letor:featureDisable=6,9,12,15" disables all Indri features. If this parameter is missing, use all features.
     * letor:svmRankLearnPath= A path to the svm_rank_learn executable.
     * letor:svmRankClassifyPath= A path to the svm_rank_classify executable.
     * letor:svmRankParamC= The value of the c parameter for SVMrank. 0.001 is a good default.
     * letor:svmRankModelFile= The file where svm_rank_learn will write the learned model.
     * letor:testingFeatureVectorsFile= The file of feature vectors that your software will write for the testing queries.
     * letor:testingDocumentScores= The file of document scores that svm_rank_classify will write for the testing feature vec
     */
    String trainingQueryFile, trainingQrelsFile, trainingFeatureVecotosFile, featureDisable;
    String trecEvalOutputPath, queryFilePath;
    String svmRankLearnPath, svmRankClassifyPath, svmRankParamC,svmRankModelFile;
    String testingFeatureVectorsFile, testingDocumentScores;
    double k_1, b, k_3, mu, lambda;

    /**
     *
     * @param parameters
     */
    public RetrievalModelLetor(Map<String, String> parameters){

        trecEvalOutputPath = parameters.get("trecEvalOutputPath");
        queryFilePath = parameters.get("queryFilePath");
        trainingQueryFile = parameters.get("letor:trainingQueryFile");
        trainingQrelsFile = parameters.get("letor:trainingQrelsFile");
        trainingFeatureVecotosFile = parameters.get("letor:trainingFeatureVectorsFile");
        featureDisable = parameters.get("letor:featureDisable");
        svmRankLearnPath = parameters.get("letor:svmRankLearnPath");
        svmRankClassifyPath = parameters.get("letor:svmRankClassifyPath");
        svmRankParamC = parameters.get("letor:svmRankParamC");
        svmRankModelFile = parameters.get("letor:svmRankModelFile");
        testingFeatureVectorsFile = parameters.get("letor:testingFeatureVectorsFile");
        testingDocumentScores = parameters.get("letor:testingDocumentScores");
        mu = Double.valueOf(parameters.get("Indri:mu"));
        lambda = Double.valueOf(parameters.get("Indri:lambda"));
        k_1 = Double.valueOf(parameters.get("BM25:k_1"));
        k_3 = Double.valueOf(parameters.get("BM25:k_3"));
        b = Double.valueOf(parameters.get("BM25:b"));
        assert k_1 >= 0.0 && b >= 0.0 && b <= 1.0 && k_3 >= 0;
        assert mu >= 0 && lambda >= 0 && lambda <= 1.0;
    }

    /**
     *
     * @return
     */
    @Override
    public String defaultQrySopName() {
        return "";
    }
}
