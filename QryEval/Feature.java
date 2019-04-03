import java.io.*;
import java.util.*;

public class Feature {
    Set<Integer> featureDisable = new HashSet<>();
    // Map<queryid, Map<external_docid, relevance_score>>
    Map<Integer, Map<String, Integer>> relMap = new HashMap<>();
    TreeMap<Integer, String[]> inputTrainQuery = new TreeMap<>();
    RetrievalModelLetor model;

    public Feature(RetrievalModelLetor model) throws IOException {
        this.model = model;
        if(model.featureDisable != null){
            featureDisable = new HashSet<>();
            for(String s: model.featureDisable.split(",")){
                featureDisable.add(Integer.parseInt(s.trim()));
            }
        }
    }

    /**
     * To read relevance judgement and store the results in relMap.
     * Map<queryid, Map<external_docid, relevance_score>>.
     *
     * @param model
     */
    private void readTrainingRelevanceData(RetrievalModelLetor model) {
        BufferedReader brRelevanceFB;
        try {
            brRelevanceFB = new BufferedReader(new FileReader(model.trainingQrelsFile));

            //Map of query id and map of doc id & relevance score
            String line = "";
            while ((line = brRelevanceFB.readLine()) != null) {
                int queryID = Integer.valueOf(line.split("\\s+")[0]);
                String externalDocid = line.split("\\s+")[2];
                int relScore = Integer.valueOf(line.split("\\s+")[3]) + 3;
                Map<String, Integer> docRel = new HashMap<>();
                if(relMap.containsKey(queryID)){
                    docRel = relMap.get(queryID);
                }
                docRel.put(externalDocid , relScore);
                relMap.put(queryID, docRel);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private TreeMap<Integer, String[]> readQuery(String queryFile) {
        BufferedReader brTrainingQuery = null;
        try{
            brTrainingQuery = new BufferedReader(new FileReader(queryFile));
            //Map of query id and tokenized query content
            inputTrainQuery = new TreeMap<>();
            String query = "";
            while ((query = brTrainingQuery.readLine()) != null) {
                String[] tokenizedQuery = QryParser.tokenizeString(query.split(":")[1]);
                inputTrainQuery.put(Integer.valueOf(query.split(":")[0].trim()), tokenizedQuery);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return  inputTrainQuery;

    }

    public Map<Integer, List<String>> getRegularFeatures(RetrievalModelLetor model){
        return getFeatures(model, model.queryFilePath, model.testingFeatureVectorsFile);
    }

    public void getTrainFeatures(RetrievalModelLetor model){
        getFeatures(model, model.trainingQueryFile, model.trainingFeatureVecotosFile);
    }

    /**
     *
     * @param model
     * @param inputPath
     * @param outputPath
     * @return
     */
    public Map<Integer, List<String>> getFeatures(RetrievalModelLetor model, String inputPath, String outputPath){
        //Read training queries and get a map sorted based on query id
        //Get the sorted query ids
        TreeMap<Integer, String[]> queries = readQuery(inputPath);

        Set<Integer> queryIDset = queries.keySet();
        int[] queryID = new int[queryIDset.size()];
        Iterator<Integer> itr = queryIDset.iterator();
        int counter = 0;
        while(itr.hasNext()){
            queryID[counter++] = itr.next();
        }

        //Read relevance judgement and store results in relMap
        //Map<queryid, Map<external_docid, relevance_score>>.
        readTrainingRelevanceData(model);

        //Document list to store <Query id, external doc id list> pairs
        Map<Integer, List<String>> docList = new TreeMap<>();

        //To write the results into training feature vector file and sorted by query id
        BufferedWriter bw = null;

        try{
            bw = new BufferedWriter(new FileWriter(outputPath));
            //calculate all feature vector and store min and max for normalization
            //query id should be sorted
            for(int qID: queryID){
                List<String> docs = new ArrayList<>();
                //for each query
                Map<String, Integer> docidRelMap = relMap.get(qID);
                Map<String, Map<Integer, Double>> docidFeatureMap = new TreeMap<>();

                //Caculate the feature scores for each doc
                for(Map.Entry<String, Integer> docRelEntry: docidRelMap.entrySet()){
                    //get original feature id and feature score
                    Map<Integer, Double> featureMap = calculateFeatures(qID, docRelEntry.getKey());
                    //doc id doesn't exist
                    if(featureMap == null) continue;
                    docidFeatureMap.put(docRelEntry.getKey(), featureMap);
                    docs.add(docRelEntry.getKey());
                }
                docList.put(qID, docs);


                //normalize for each doc in one query
                normalize(docidFeatureMap);

                //write this query result into file
                //2 qid:1 1:1 2:1 3:0 4:0.2 5:0 # clueweb09-en0000-48-24794
                for(Map.Entry<String, Map<Integer, Double>> entryDoc: docidFeatureMap.entrySet()){
                    Map<Integer, Double> featureMap = entryDoc.getValue();
                    String res = "" + docidRelMap.get(entryDoc.getKey()) + " qid:" + qID;
                    for(Map.Entry<Integer, Double> entry: featureMap.entrySet()){
                        double val = (entry.getValue() == null)? 0.0: entry.getValue();
                        res += " " + entry.getKey() + ":" + val;
                    }
                    res += " # " + entryDoc.getKey()+ "\n";
                    bw.write(res);
                }
                bw.flush();
            }
            bw.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return docList;
    }



    private void normalize(Map<String, Map<Integer, Double>> docidFeatureMap) {
        int featureNum = 19;
        double[] max = new double[featureNum];
        double[] min = new double[featureNum];
        for(int i = 1; i < featureNum; i++ ){
            max[i] = Double.MIN_VALUE;
            min[i] = Double.MAX_VALUE;
        }

        for(Map.Entry<String, Map<Integer, Double>> entry: docidFeatureMap.entrySet()){
            Map<Integer, Double> featureMap = entry.getValue();

            for(int i = 1; i < featureNum; i++){
                if(featureMap.containsKey(i) && featureMap.get(i) != Double.MIN_VALUE){
                    max[i] = Math.max(featureMap.get(i), max[i]);
                    min[i] = Math.min(featureMap.get(i), min[i]);
                }
            }
        }

        for(Map.Entry<String, Map<Integer, Double>> entry: docidFeatureMap.entrySet()){
            String externalDocid = entry.getKey();

            Map<Integer, Double> featureMap = entry.getValue();
            Map<Integer, Double> newFeatureMap = new TreeMap<>();
            for (int idx = 1; idx < featureNum; idx++) {
                double minVal = min[idx];
                double maxVal = max[idx];
                double normVal = 0.0;
                double score = featureMap.containsKey(idx) ? featureMap.get(idx): Double.MIN_VALUE;
                if (score != Double.MIN_VALUE) {
                    normVal = (maxVal == minVal) ? 0.0 : ((score - minVal) / (maxVal - minVal));
                }
                newFeatureMap.put(idx, normVal);
            }

            docidFeatureMap.put(externalDocid, newFeatureMap);
        }

    }



    /**
     *
     * @param queryID
     * @param externalDocID
     * @return Map<feature id, feature score>
     * @throws Exception
     */
    private Map<Integer, Double> calculateFeatures(int queryID, String externalDocID) throws Exception {
        int docid = Idx.getInternalDocid(externalDocID);
        if (docid == -1){
            return null;
        }

        Map<Integer, Double> vec = new TreeMap<>();

        // f1: Spam score for d (read from index).
        // int spamScore = Integer.parseInt (Idx.getAttribute("spamScore", docid));
        if (!this.featureDisable.contains(1)) {
            Double spamScore = Double.parseDouble(Idx.getAttribute("spamScore", docid));
            vec.put(1, spamScore);
        }

        String rawUrl = Idx.getAttribute("rawUrl", docid);
        rawUrl = rawUrl.replaceAll("http://", "");
        // f2: Url depth for d(number of '/' in the rawUrl field).
        // String rawUrl = Idx.getAttribute ("rawUrl", docid);
        if (!this.featureDisable.contains(2)) {
            double urlDepth = (double) rawUrl.length() - (double) rawUrl.replace("/","").length();
            vec.put(2, urlDepth);
        }

        // f3: FromWikipedia score for d (1 if the rawUrl contains "wikipedia.org" otherwise 0).
        if (!this.featureDisable.contains(3)) {
            double wikiScore = rawUrl.contains("wikipedia.org") ? 1 : 0;
            vec.put(3, wikiScore);
        }
        // f4: PageRank score for d (read from file).
        if (!this.featureDisable.contains(4)) {
            double pageRank = Double.parseDouble (Idx.getAttribute ("PageRank", docid));
            vec.put(4, pageRank);
        }

        String[] fields = { "body", "title", "url", "inlink" };
        // f5-f16
        for (int i = 0; i < fields.length; i++) {
            // f5: BM25 score for <q, field>.
            if (!this.featureDisable.contains(5 + i * 3)) {
                double score = getScoreBM25(model, docid, fields[i], inputTrainQuery.get(queryID));
                if(score != Double.MIN_VALUE) vec.put(5 + i * 3, score);
            }
            // f6: Indri score for <q, dbody>.
            if (!this.featureDisable.contains(6 + i * 3)) {
                double score = getScoreIndri(model, docid, fields[i], inputTrainQuery.get(queryID));
                if(score != Double.MIN_VALUE) vec.put(6 + i * 3, score);
            }
            // f7: Term overlap score for <q, dbody>.
            if (!this.featureDisable.contains(7 + i * 3)) {
                double score = getScoreOverlap(docid, fields[i], inputTrainQuery.get(queryID));

                if(score != Double.MIN_VALUE) vec.put(7 + i * 3, score);

            }
        }


        // f17: A custom feature - use your imagination.
        // tfidf score
        if (!this.featureDisable.contains(17)) {
            double score = getScoreTotalTf(rawUrl);
            vec.put(17, score);
        }
        // f18: A custom feature - use your imagination.
        if (!this.featureDisable.contains(18)) {
            double score = getScoreTfidf(docid, "body", inputTrainQuery.get(queryID));
            vec.put(18, score);
        }

        return vec;

    }

    public double getScoreBM25(RetrievalModel r, int docid, String field, String[] qTerms) throws IOException {
        double score = 0;

        TermVector vec = new TermVector(docid, field);
        Set<String> termSet = new HashSet<String>(Arrays.asList(qTerms));

        if (vec.positionsLength() == 0 || vec.stemsLength() == 0)
            return Double.MIN_VALUE;

        double k_1 = ((RetrievalModelLetor) r).k_1;
        double b = ((RetrievalModelLetor) r).b;
        double k_3 = ((RetrievalModelLetor) r).k_3;

        for(int i = 0; i < vec.stemsLength(); i++){
            if(termSet.contains(vec.stemString(i))) {
                double tf = vec.stemFreq(i);
                double idf = Math.max(0, Math.log((Idx.getNumDocs() - vec.stemDf(i) + 0.5) / (vec.stemDf(i) + 0.5)));

                double tf_weight = tf / (tf + k_1 * (1 - b + b * Idx.getFieldLength(field, docid) / (Idx.getSumOfFieldLengths(field) / (double) Idx.getDocCount(field))));
                double user_weight = (k_3 + 1) * 1 / (k_3 + 1);

                score += idf * tf_weight * user_weight;
            }
        }
        return score;
    }

    public double getScoreIndri(RetrievalModel r, int docid, String field, String[] terms) throws IOException {
        TermVector vec = new TermVector(docid, field);
        if (vec.stemsLength() == 0)
            return Double.MIN_VALUE;

        double score = 1d;
        List<String> termDiff = new ArrayList<>();

        // Model parameters
        double mu = ((RetrievalModelLetor) r).mu;
        double lambda = ((RetrievalModelLetor) r).lambda;

        // Idx parameters
        double collLen = Idx.getSumOfFieldLengths(field);
        double docLen = Idx.getFieldLength(field, docid);

        for (String stem : terms) {

            int i = vec.indexOfStem(stem);

            if (i == -1) {
                termDiff.add(stem);
            } else {

                double tf = vec.stemFreq(i);
                double ctf = Idx.getTotalTermFreq(field, stem);
                if(ctf == 0d) ctf = 0.5;
                double mle = ctf / collLen;

                double dirichletPrior = (tf + mu * mle) / (docLen + mu);
                double mixture = (1 - lambda) * dirichletPrior + lambda * mle;
                score *= mixture;

            }

        }

        // if (termIntersection.size() == 0) return 0;
        if (termDiff.size() == terms.length) return 0;

        // set ctf = 0.5 when ctf = 0 (tf = 0)
        // which only happens when we call the `getDefaultScore` method
        for (String term : termDiff) {

            double tf = 0;
            double ctf = Idx.getTotalTermFreq(field, term);
            if(ctf == 0d) ctf = 0.5;
            double mle = ctf / collLen;

            double dirichletPrior = (tf + mu * mle) / (docLen + mu);
            double mixture = (1 - lambda) * dirichletPrior + lambda * mle;
            score *= mixture;

        }

        score = Math.pow(score, 1.0 / terms.length);
        return score;
    }

    public double getScoreOverlap(int docid, String field, String[] qTerms) throws IOException {
        if (qTerms.length < 1)
            return 0;
        int count = 0;
        TermVector vec = new TermVector(docid, field);
        if (vec.positionsLength() == 0 || vec.stemsLength() == 0)
            return Double.MIN_VALUE;

        for (String stem : qTerms) {
            int i = vec.indexOfStem(stem);
            if (i == -1)
                continue;
            count++;
        }
        return count / (double) qTerms.length;
    }

    private double getScoreTotalTf(String rawUrl) throws IOException {
        double length = rawUrl.length();
        double letter = 0;
        for(char c: rawUrl.toCharArray()){
            if(Character.isLetter(c)){
                letter++;
            }
        }
        return letter/length;
    }

    private double getScoreTfidf(int docid, String field, String[] querys) throws IOException {
        double score = 0;
        TermVector vec = new TermVector(docid, field);
        for (String stem : querys) {
            //if no stem, i will be -1 for arkansas
            int i = vec.indexOfStem(stem);
            if (i == -1)
                continue;

            double idf = Math.max(0, Math.log((Idx.getNumDocs() - vec.stemDf(i) + 0.5) / (vec.stemDf(i) + 0.5)));
            score += vec.stemFreq(i) * idf;
        }

        return score;
    }

}
