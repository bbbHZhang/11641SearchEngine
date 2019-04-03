/*
 *  Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.3.3.
 */

import java.io.*;
import java.util.*;

/**
 *  This software illustrates the architecture for the portion of a
 *  search engine that evaluates queries.  It is a guide for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 */
public class QryEval {

  //  --------------- Constants and variables ---------------------

  private static final String USAGE =
    "Usage:  java QryEval paramFile\n\n";

  private static final String[] TEXT_FIELDS =
    { "body", "title", "url", "inlink" };

  private static Map<Integer, ScoreList> scoreListMap = null;




  //  --------------- Methods ---------------------------------------

  /**
   *  @param args The only argument is the parameter file name.
   *  @throws Exception Error accessing the Lucene index.
   */
  public static void main(String[] args) throws Exception {

    //  This is a timer that you may find useful.  It is used here to
    //  time how long the entire program takes, but you can move it
    //  around to time specific parts of your code.
    
    Timer timer = new Timer();
    timer.start ();

    //  Check that a parameter file is included, and that the required
    //  parameters are present.  Just store the parameters.  They get
    //  processed later during initialization of different system
    //  components.

    if (args.length < 1) {
      throw new IllegalArgumentException (USAGE);
    }
    //args[0] is the file path
    Map<String, String> parameters = readParameterFile (args[0]);

    //  Open the index and initialize the retrieval model.
    Idx.open (parameters.get("indexPath"));
    RetrievalModel model = initializeRetrievalModel (parameters);

    //  Perform experiments.
    if(!(model instanceof RetrievalModelLetor)){
      processQueryFile(parameters, model);
    }


    //  Clean up.
    
    timer.stop ();
    System.out.println ("Time:  " + timer);
  }


  /**
   *  Allocate the retrieval model and initialize it using parameters
   *  from the parameter file.
   *  @return The initialized retrieval model
   *  @throws IOException Error accessing the Lucene index.
   */
  private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
          throws Exception {

    RetrievalModel model = null;
    String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();

    if (modelString.equals("unrankedboolean")) {
      model = new RetrievalModelUnrankedBoolean();
    } else if (modelString.equals("rankedboolean")){
      model = new RetrievalModelRankedBoolean();
    } else if (modelString.equals("indri")){
      model = new RetrievalModelIndri(Float.valueOf(parameters.get("Indri:mu")),
                                      Float.valueOf(parameters.get("Indri:lambda")));
    } else if (modelString.equals("bm25")) {
      model = new RetrievalModelBM25(Double.valueOf(parameters.get("BM25:k_1")),
              Double.valueOf(parameters.get("BM25:k_3")), Double.valueOf(parameters.get("BM25:b")));
    } else if(modelString.equals("letor")){

      /********************************************** HW4 *****************************************/
      System.out.println("Starting Learn to Rank Training");
      model = new RetrievalModelLetor(parameters);
      Feature fv = new Feature((RetrievalModelLetor) model);
      fv.getTrainFeatures((RetrievalModelLetor) model);
      trainSVM((RetrievalModelLetor) model);
      System.out.println("Train SVM Done");

      RetrievalModel bm25Model = new RetrievalModelBM25(((RetrievalModelLetor) model).k_1,
              ((RetrievalModelLetor) model).b, ((RetrievalModelLetor) model).k_3);

      //model.testingFeatureVectorsFile
      //model.testingDocumentScores

      Map<Integer, Map<String, Integer>> relMap = processQueryFile(parameters, bm25Model);
      fv.relMap = relMap;
      Map<Integer,List<String>> docList = fv.getRegularFeatures((RetrievalModelLetor) model);
      testSVM((RetrievalModelLetor) model);
      System.out.println("SVM test completed");

      readLetorScore((RetrievalModelLetor) model, docList, parameters);

    } else {
      throw new IllegalArgumentException
        ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
    }
      
    return model;
  }

  private static void readLetorScore(RetrievalModelLetor model, Map<Integer, List<String>> docList, Map<String, String> parameters) {
    //doclist <QueryID, external doc id list>
    try {
      //svmScores is the all socres of all documents
      ArrayDeque<Double> svmScores = readScores(model.testingDocumentScores);
      Iterator<Map.Entry<Integer, List<String>>> iter = docList.entrySet().iterator();
      BufferedWriter output = new BufferedWriter(new FileWriter(model.trecEvalOutputPath));

      while (iter.hasNext()) {
        Map.Entry<Integer, List<String>> entry = iter.next();
        List<String> docs = entry.getValue();
        int len = docs.size();
        ScoreList r = new ScoreList();
        for (int i = 0; i < len; i++) {
          if (!svmScores.isEmpty()) {
            r.add(Idx.getInternalDocid(docs.get(i)), svmScores.pollFirst());
          } else {
            System.out.println("Empty");
          }
        }
        if (r != null) {
          r.sort();
          printResults(entry.getKey()+"" , r, parameters, output);
          System.out.println();
        }
      }

      output.close();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }


  }

  private static ArrayDeque<Double> readScores(String testingDocumentScores) throws IOException {
    BufferedReader input = null;
    // List<Double> svmScores = new ArrayList<>();
    ArrayDeque<Double> svmScores = new ArrayDeque<>();

    try {
      String docScore = null;

      input = new BufferedReader(new FileReader(testingDocumentScores));

      int count = 0;
      while ((docScore = input.readLine()) != null) {
        count++;
        svmScores.offer(Double.parseDouble(docScore.trim()));
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      input.close();
      return svmScores;
    }

  }

  public static void trainSVM(RetrievalModelLetor model) throws Exception {
    Process cmdProc = Runtime.getRuntime().exec(new String[] { model.svmRankLearnPath, "-c",
            String.valueOf(model.svmRankParamC), model.trainingFeatureVecotosFile, model.svmRankModelFile });

    // consume stdout and print it out for debugging purposes
    BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
    String line;
    while ((line = stdoutReader.readLine()) != null) {
      System.out.println(line);
    }
    // consume stderr and print it for debugging purposes
    BufferedReader stderrReader = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
    while ((line = stderrReader.readLine()) != null) {
      System.out.println(line);
    }

    // get the return value from the executable. 0 means success, non-zero
    // indicates a problem
    int retValue = cmdProc.waitFor();
    if (retValue != 0) {
      throw new Exception("SVM Rank crashed.");
    }
  }

  public static void testSVM(RetrievalModelLetor model) throws Exception {
    Process cmdProc = Runtime.getRuntime().exec(new String[] { model.svmRankClassifyPath,
            model.testingFeatureVectorsFile, model.svmRankModelFile, model.testingDocumentScores });
    // consume stdout and print it out for debugging purposes
    BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
    String line;
    while ((line = stdoutReader.readLine()) != null) {
      System.out.println(line);
    }
    // consume stderr and print it for debugging purposes
    BufferedReader stderrReader = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
    while ((line = stderrReader.readLine()) != null) {
      System.out.println(line);
    }

    // get the return value from the executable. 0 means success, non-zero
    // indicates a problem
    int retValue = cmdProc.waitFor();
    if (retValue != 0) {
      throw new Exception("SVM Rank crashed.");
    }
  }


  /**
   * Print a message indicating the amount of memory used. The caller can
   * indicate whether garbage collection should be performed, which slows the
   * program but reduces memory usage.
   * 
   * @param gc
   *          If true, run the garbage collector before reporting.
   */
  public static void printMemoryUsage(boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc)
      runtime.gc();

    System.out.println("Memory used:  "
        + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
    }

  /**
   * Process one query.
   * @param qString A string that contains a query.
   * @param model The retrieval model determines how matching and scoring is done.
   * @return Search results
   * @throws IOException Error accessing the index
   */
  static ScoreList processQuery(String qString, RetrievalModel model)
    throws IOException {

    String defaultOp = model.defaultQrySopName ();
    qString = defaultOp + "(" + qString + ")";
    Qry q = QryParser.getQuery (qString);

    // Show the query that is evaluated
    
    System.out.println("    --> " + q);
    
    if (q != null) {

      ScoreList r = new ScoreList ();
      
      if (q.args.size () > 0) {		// Ignore empty queries

        q.initialize (model);

        while (q.docIteratorHasMatch (model)) {
          int docid = q.docIteratorGetMatch ();

          double score = ((QrySop) q).getScore (model);
          r.add (docid, score);
          q.docIteratorAdvancePast (docid);
        }
      }
      r.sort();
      return r;
    } else
      return null;
  }

  /**
   *  Process the query file.
   *  @param parameters
   *  @param model
   *  @throws IOException Error accessing the Lucene index.
   */
  static Map<Integer, Map<String, Integer>> processQueryFile(Map<String, String> parameters, RetrievalModel model) throws IOException {

    BufferedReader input = null;
    BufferedWriter writer = null;

    Map<Integer, Map<String, Integer>> relMap = new TreeMap<>();

    try {
      String qLine = null;

      input = new BufferedReader(new FileReader(parameters.get("queryFilePath")));
      writer = new BufferedWriter(new FileWriter(parameters.get("trecEvalOutputPath")));

      //  Each pass of the loop processes one query.
      while ((qLine = input.readLine()) != null) {
        int d = qLine.indexOf(':');

        if (d < 0) {
          throw new IllegalArgumentException
            ("Syntax error:  Missing ':' in query line.");
        }

        printMemoryUsage(false);

        String qid = qLine.substring(0, d);
        String query = qLine.substring(d + 1);

        System.out.println("Query " + qLine);

        ScoreList r = null;

        /*********************************** Homework3 Expand Queries Start *******************************************/
        if(!parameters.containsKey("fb") || parameters.get("fb").equals("false")){
          //use the query to retrieve documents
          r = processQuery(query, model);
        }else{
          //check all parameters for expansion
          if (!(parameters.containsKey("fbTerms") && parameters.containsKey("fbMu")
                  && parameters.containsKey("fbOrigWeight")
                  && parameters.containsKey("fbExpansionQueryFile"))) {
            throw new IllegalArgumentException("Required parameters were missing from the parameter file.");
          }

          BufferedWriter bw = new BufferedWriter(new FileWriter(parameters.get("fbExpansionQueryFile"), true));

          if(parameters.containsKey("fbInitialRankingFile")){
            //read a document ranking in trec_eval input format from the fbInitialRankingFile;
            if(scoreListMap == null){
                scoreListMap = readRankingListFile(parameters.get("fbInitialRankingFile"));
            }
            r = scoreListMap.get(Integer.valueOf(qid));
          }else{
            //use the query to retrieve documents
            r = processQuery(query, model);
          }

          //  use the Indri query expansion algorithm (Lecture 11, slides #30-36) to produce an expanded query;
          //  write the expanded query to a file specified by the fbExpansionQueryFile= parameter (the format is below);
          //  create a combined query as #wand (w qoriginal + (1-w) qexpandedquery);
          //  use the combined query to retrieve documents;

          //r is the sorted docid with scores
          String expandedQuery = expandQuery(r, parameters);

          System.out.println(qid + ": " + expandedQuery + "\n");

          bw.write(qid + ": " + expandedQuery + "\n");

          double fbOrigWeight = Double.valueOf(parameters.get("fbOrigWeight"));
          if(query.trim().charAt(0) != '#') query = model.defaultQrySopName() + "(" + query +")";
          String newQry = String.format("#wand (%f %s %f %s )", fbOrigWeight, query, 1-fbOrigWeight, expandedQuery);
          System.out.println(newQry);
          r = processQuery(newQry, model);

          bw.close();
        }
        //write the retrieval results to a file in trec_eval input format;
        /*********************************** Homework3 Expand Queries End *******************************************/

        Map<String, Integer> topDocs = new HashMap<String, Integer>();

        if (r != null) {
          int result_range = Math.min(100, r.size());
          if (r.size() < 1) {
            System.out.println("\tNo results.");
          } else {
            for (int i = 0; i < result_range; i++) {
              topDocs.put(Idx.getExternalDocid(r.getDocid(i)), 0);
            }
          }
        }
        relMap.putIfAbsent(Integer.parseInt(qid), topDocs);

        if (r != null) {
          printResults(qid, r, parameters, writer);
          System.out.println();
        }

      }
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      input.close();
      writer.close();
      return relMap;
    }
  }

  /**
   * For hw3 query expansion.
   *
   * @param r
   * @param parameters
   * @return
   * @throws IOException
   */
  private static String expandQuery(ScoreList r, Map<String, String> parameters) throws IOException {
    //To calculate term score P(t|I): indri score, p(t|d) = (tf + mu*pmle(t|C))/(mu+length(d)), log(length terms (C)/ctf)
    int docNum = Math.min(Integer.parseInt(parameters.get("fbDocs")), r.size());
    int fbTerms = Integer.parseInt(parameters.get("fbTerms"));
    int fbMu = Integer.parseInt(parameters.get("fbMu"));

    Map<String, ArrayList<Integer>> invertedLists = new HashMap();
    Map<String, Double> termScores = new HashMap<>();
    Map<String, Double> mleList = new HashMap<>();

    double lengthC = Idx.getSumOfFieldLengths("body");

    for(int i =0; i < docNum; i++){
      int docid = r.getDocid(i);

      TermVector termVector = new TermVector(docid, "body");
      double docScore = r.getDocidScore(i);
      double docLength = Idx.getFieldLength("body", docid);
      for(int j = 0; j < termVector.stemsLength(); j++){
        String term = termVector.stemString(j);
        //the first one in this termVector is null???????????????????????
        if(term == null || term.contains(".")||term.contains(","))continue;
        long tf = termVector.stemFreq(j);
        long ctf = termVector.totalStemFreq(j);
        double ptd = (tf + fbMu * ctf / lengthC)/(fbMu + docLength);
        double idf = Math.log(lengthC/ctf);
        double termScore = docScore * ptd * idf;
        if(termScores.containsKey(term)){
          termScores.put(term, termScores.get(term) + termScore);
        }else{
          termScores.put(term, termScore);
        }

        ArrayList<Integer> newInvertedList = new ArrayList<>();
        if(invertedLists.containsKey(term)){
          newInvertedList = invertedLists.get(term);
        }
        newInvertedList.add(docid);
        invertedLists.put(term, newInvertedList);

        mleList.putIfAbsent(term, ctf/lengthC);
      }
    }

    //set score for documents whose tf = 0;
    for(String term: invertedLists.keySet()){
      ArrayList<Integer> documentList = invertedLists.get(term);
      for(int a = 0; a < docNum; a++){
        int docid = r.getDocid(a);
        if(documentList.contains(docid))continue;;
        long tf = 0;
        double ptd = (tf + fbMu * mleList.get(term))/(Idx.getFieldLength("body", docid) + fbMu);
        double idf = Math.log(1/mleList.get(term));
        double docScore = ptd * r.getDocidScore(a) * idf;
        if(termScores.containsKey(term)){
          termScores.put(term, termScores.get(term) + docScore);
        }else{
          termScores.put(term, docScore);
        }
      }
    }

    //get top k terms
    PriorityQueue<Map.Entry<String,Double>> termPriorityQueue = new PriorityQueue<>(new Comparator<Map.Entry<String, Double>>() {
      @Override
      public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
        return o2.getValue().compareTo(o1.getValue());
      }
    });
    termPriorityQueue.addAll(termScores.entrySet());

    //Expanded Query
    String newQuery = "";
    for(int i = 0 ; i < fbTerms; i++){
      newQuery = String.format("%.4f", termPriorityQueue.peek().getValue())
              + " " + termPriorityQueue.peek().getKey() + " " + newQuery;
      termPriorityQueue.poll();
    }
    newQuery = "#wand (" + newQuery + ")";
    return newQuery;
  }

  /**
   * Read from ranking list file and add all qry_number and score list pairs to map.
   *
   * @param fbInitialRankingFile file address to read.
   * @return result map
   */
  private static Map<Integer,ScoreList> readRankingListFile(String fbInitialRankingFile) {
    Map<Integer,ScoreList> res = new HashMap<>();
    try (BufferedReader br = new BufferedReader(new FileReader(new File((fbInitialRankingFile))))) {
      String line;
      int old_qry = -1;
      ScoreList scoreList = new ScoreList();
      while((line = br.readLine()) != null){
        String[] lines = line.split("\\s+");
        int new_qry = Integer.parseInt(lines[0].trim());
        if(old_qry == -1){
          old_qry = new_qry;
        }
        if(new_qry != old_qry){
          res.put(old_qry, scoreList);
          old_qry = new_qry;
          scoreList = new ScoreList();
        }
        scoreList.add(Idx.getInternalDocid(lines[2].trim()), Double.parseDouble(lines[4].trim()));
      }
      res.put(old_qry, scoreList);

    } catch (FileNotFoundException e) {
      System.out.println("Exception during reading ranking list file " + e.getMessage());
    } catch (IOException e) {
      System.out.println("Exception during reading ranking list file " + e.getMessage());
    } catch (Exception e) {
      e.printStackTrace();
    }
    return res;
  }

  /**
   * Print the query results.
   * 
   * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
   * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
   * 
   * QueryID Q0 DocID Rank Score RunID
   * 
   * @param queryName
   *          Original query.
   * @param result
   *          A list of document ids and scores
   * @param parameters
   *          The parameters map
   * @param writer
   *          The file writer
   * @throws IOException Error accessing the Lucene index.
   */
  static void printResults(String queryName, ScoreList result, Map<String, String> parameters, BufferedWriter writer) throws IOException {


    System.out.println(queryName + ":  ");
    if (result.size() < 1) {
      String out0 = String.format("%s Q0 dummy 1 0 fubar%n", queryName);
      System.out.print(out0);
      writer.write(out0);
    } else {
      String ForR = (parameters.containsKey("fb") && parameters.get("fb").equals("true"))? "reference": "fubar";
      if(parameters.get("retrievalAlgorithm").equals("letor")){
        ForR = "yubinletor";
      }

      for (int i = 0; i < Math.min(result.size(), Integer.parseInt(parameters.get("trecEvalOutputLength"))); i++) {
        String out = String.format("%s Q0 %s %d %.15f %s%n", queryName, Idx.getExternalDocid(result.getDocid(i)), i + 1, result.getDocidScore(i), ForR);
        writer.write(out);
//        System.out.print(out);
      }
      //706 Q0 GX004-64-16738159 1 5.000000000000000000 fubar
    }
  }

  /**
   *  Read the specified parameter file, and confirm that the required
   *  parameters are present.  The parameters are returned in a
   *  HashMap.  The caller (or its minions) are responsible for processing
   *  them.
   *  @return The parameters, in <key, value> format.
   */
  private static Map<String, String> readParameterFile (String parameterFileName)
    throws IOException {

    Map<String, String> parameters = new HashMap<String, String>();

    File parameterFile = new File (parameterFileName);

    if (! parameterFile.canRead ()) {
      throw new IllegalArgumentException
        ("Can't read " + parameterFileName);
    }

    Scanner scan = new Scanner(parameterFile);
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split ("=");
      String paraName = pair[0].trim();
      String paraValue = pair[1].trim();
      parameters.put(paraName, paraValue);
    } while (scan.hasNext());

    scan.close();

    if (! (parameters.containsKey ("indexPath") &&
           parameters.containsKey ("queryFilePath") &&
           parameters.containsKey ("trecEvalOutputPath") &&
           parameters.containsKey ("retrievalAlgorithm") &&
            parameters.containsKey("trecEvalOutputLength"))) {
      throw new IllegalArgumentException
        ("Required parameters were missing from the parameter file.");
    }

    return parameters;
  }

}
