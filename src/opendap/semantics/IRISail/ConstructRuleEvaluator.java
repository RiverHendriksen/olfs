package opendap.semantics.IRISail;

import org.openrdf.model.*;
import org.openrdf.model.impl.StatementImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.query.*;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.Sail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: ndp
 * Date: Aug 26, 2010
 * Time: 12:15:38 PM
 * To change this template use File | Settings | File Templates.
 */
public class ConstructRuleEvaluator {
    private  Logger log = LoggerFactory.getLogger(IRISailRepository.class);

    public static enum ProcessingTypes {
        NONE, xsString, DropQuotes, RetypeTo, Increment, Function
    }

    private Vector<String> constructQuery;
    private HashMap<String, String> constructContext;

    private ProcessingTypes postProcessFlag;


    public ConstructRuleEvaluator() {
        log = LoggerFactory.getLogger(getClass());
        constructQuery = new Vector<String>();
        constructContext = new HashMap<String, String>();

    }




    /*
     * Run all Construct queries and statement into repository
     */

    public  void runConstruct(SailRepository repository) throws RepositoryException {

        log.debug("-----------------------------------------------------------------");
        log.debug("------------------- Entering runConstruct() ---------------------");
        log.debug("-----------------------------------------------------------------");

        GraphQueryResult graphResult = null;
        RepositoryConnection con = null;
        Vector<Statement> Added = new Vector<Statement>();

        Boolean modelChanged = true;
        int runNbr = 0;
        int runNbrMax = 20;
        long startTime, endTime;
        startTime = new Date().getTime();

        int queryTimes = 0;
        long ruleStartTime, ruleEndTime;
        int totalStAdded = 0; // number of statements added
        int totalStAddedIn1Pass = 0; // number of statements added in 1 PASS

        findConstruct(repository);

         //log.debug("Before running the construct rules:\n " +
         //opendap.coreServlet.Util.getMemoryReport());
        con = repository.getConnection();

        while (modelChanged && runNbr < runNbrMax) {

            runNbr++;
            modelChanged = false;
            totalStAddedIn1Pass = 0;
            log.info("Total construct rule number =  " + this.constructQuery.size());
            //log.debug("Applying Construct Rules. Beginning Pass #" + runNbr
            //        + " \n" + opendap.coreServlet.Util.getMemoryReport());
            int ruleNumber = 0;
            for (String qstring : this.constructQuery) {
                ruleNumber++;
                queryTimes++;
                ruleStartTime = new Date().getTime();
                int stAdded = 0; // track statements added by each rule

                Vector<Statement> toAdd = new Vector<Statement>();
                String constructURL = this.constructContext.get(qstring);

                //URI uriaddress = new URIImpl(constructURL);
                URI uriaddress = new URIImpl(Terms.externalInferencingContextUri);
                Resource[] context = new Resource[1];
                context[0] = uriaddress;

                String processedQueryString = convertSWRLQueryToSeasameQuery(qstring);

                try {
                     //log.debug("Prior to making new repository connection:\n "
                     //+ opendap.coreServlet.Util.getMemoryReport());
                    log.debug("Original construct rule ID: " + constructURL);
                    GraphQuery graphQuery = con.prepareGraphQuery(
                            QueryLanguage.SERQL, processedQueryString);

                    log.info("Querying the repository. PASS #" + queryTimes
                            + " (construct rules pass #" + runNbr + ")");

                    graphResult = graphQuery.evaluate();
                    GraphQueryResult graphResultStCount = graphQuery.evaluate();
                    log.info("Completed querying. ");

                     //log.debug("After evaluating construct rules:\n " +
                     //opendap.coreServlet.Util.getMemoryReport());

                    log.info("Post processing query result and adding statements ... ");

                    if (graphResult.hasNext()) {
                        modelChanged = true;

                        ValueFactory creatValue = repository.getValueFactory();

                        switch (postProcessFlag) {

                        case xsString:
                            RepositoryUtility.process_xsString(graphResult, creatValue, Added,
                                    toAdd, con, context);
                            //log.debug("After processing xs:string:\n "
                            //        + opendap.coreServlet.Util
                            //                .getMemoryReport());
                            break;

                        case DropQuotes:
                            process_DropQuotes(graphResult, creatValue, Added,
                                    toAdd, con, context);
                            //log.debug("After processing DropQuotes:\n "
                            //        + opendap.coreServlet.Util
                            //                .getMemoryReport());
                            break;

                        case RetypeTo:
                            process_RetypeTo(graphResult, creatValue, Added,
                                    toAdd, con, context);
                            //log.debug("After processing RetypeTo:\n "
                            //        + opendap.coreServlet.Util
                            //                .getMemoryReport());
                            break;

                        case Increment:
                            process_Increment(graphResult, creatValue, Added,
                                    toAdd, con, context);
                            //log.debug("After processing Increment:\n "
                            //        + opendap.coreServlet.Util
                            //                .getMemoryReport());
                            break;

                        case Function:

                            process_fn(graphResult, creatValue, Added, toAdd,
                                    con, context);// postpocessing Join,
                                                  // subtract, getWcsID
                            break;
                        case NONE:
                        default:
                            log.info("Adding none-postprocess statements ...");

                            con.add(graphResult, context);
                            int nonePostprocessSt = 0;
                            while (graphResultStCount.hasNext()) {
                                graphResultStCount.next();
                                nonePostprocessSt++;
                                stAdded++;
                            }
                            /*
                             * int nonePostprocessSt = 0; while
                             * (graphResult.hasNext()) { Statement st =
                             * graphResult.next(); con.add(st, context); //
                             * log.debug("Added statement = " //
                             * +st.toString()); toAdd.add(st); Added.add(st);
                             * nonePostprocessSt++; }
                             */
                            log.info("Complete adding " + nonePostprocessSt
                                    + " none-postprocess statements");
                            // log.debug("After processing default (NONE)
                            // case:\n " +
                            // opendap.coreServlet.Util.getMemoryReport());

                            break;
                        }

                        // log.info("Adding statements ...");
                        stAdded = 0;
                        if (toAdd != null) {
                            // con.add(toAdd, context);
                            log.info("Total added " + toAdd.size()
                                    + " statements.");
                            /*
                             * for(Statement sttoadd:toAdd){ log.debug("Add
                             * statement: "+sttoadd.toString()); }
                             */
                            stAdded = toAdd.size();
                        }

                    } // if (graphResult != null
                    else {
                        log.debug("No query result!");
                    }

                } catch (QueryEvaluationException e) {
                    log.error("Caught an QueryEvaluationException! Msg: "
                            + e.getMessage());

                } catch (RepositoryException e) {
                    log.error("Caught RepositoryException! Msg: "
                            + e.getMessage());
                } catch (MalformedQueryException e) {
                    log.error("Caught MalformedQueryException! Msg: "
                            + e.getMessage());
                    log.debug("MalformedQuery: " + processedQueryString);
                } finally {
                    if (graphResult != null) {
                        try {
                            graphResult.close();
                        } catch (QueryEvaluationException e) {
                            log.error("Caught a QueryEvaluationException! Msg: "
                                            + e.getMessage());
                        }
                    }

                }

                ruleEndTime = new Date().getTime();
                double ruleTime = (ruleEndTime - ruleStartTime) / 1000.0;

                //log.debug("Processed construct rule : " + processedQueryString);
                log.debug("Construct rule " + ruleNumber + " takes " + ruleTime
                        + " seconds in loop " + runNbr + " added " + stAdded
                        + " statements");

                totalStAdded = totalStAdded + stAdded;
                totalStAddedIn1Pass = totalStAddedIn1Pass+ stAdded;
            } // for(String qstring
            log.info("Completed pass " + runNbr + " of Construct evaluation"+"Queried the repository " +
                    queryTimes + " times" + " added " + totalStAddedIn1Pass + " statements");
            log.info("Queried the repository " + queryTimes + " times");

            findConstruct(repository);
        } // while (modelChanged

        try {
            con.close();
        } catch (RepositoryException e) {
            log.error("Caught a RepositoryException! Msg: " + e.getMessage());
        }
        endTime = new Date().getTime();
        double totaltime = (endTime - startTime) / 1000.0;
        log.info("In construct for " + totaltime + " seconds");
        log.info("Total number of statements added in construct: "
                + totalStAdded + " \n");

    }

    /*
     * Find all Construct queries
     */
    private void findConstruct(SailRepository repository) {
        TupleQueryResult result = null;
        RepositoryConnection con = null;
        List<String> bindingNames;

        log.debug("Locating Construct rules...");

        try {
            con = repository.getConnection();
            String queryString = "SELECT queries, contexts "
                    + "FROM "
                    + "{contexts} rdfcache:"+Terms.serqlTextType+" {queries} "
                    + "using namespace "
                    + "rdfcache = <"+ Terms.rdfCacheNamespace+">";

            log.debug("queryString: " + queryString);

            TupleQuery tupleQuery = con.prepareTupleQuery(QueryLanguage.SERQL,
                    queryString);

            result = tupleQuery.evaluate();

            if (result != null) {
                bindingNames = result.getBindingNames();

                while (result.hasNext()) {
                    BindingSet bindingSet = (BindingSet) result.next();

                    Value firstValue = bindingSet.getValue("queries");
                    if (!constructQuery.contains(firstValue.stringValue())) {
                        constructQuery.add(firstValue.stringValue());
                    }
                    //log.debug("Adding construct to import pool: "
                    //        + firstValue.toString());
                    Value secondValue = bindingSet.getValue("contexts");
                    constructContext.put(firstValue.stringValue(), secondValue
                            .stringValue());

                }
            } else {
                log.debug("No query result!");
            }
        } catch (QueryEvaluationException e) {
            log.error("Caught an QueryEvaluationException! Msg: "
                    + e.getMessage());

        } catch (RepositoryException e) {
            log.error("Caught RepositoryException! Msg: " + e.getMessage());
        } catch (MalformedQueryException e) {
            log.error("Caught MalformedQueryException! Msg: " + e.getMessage());
        }

        finally {
            if (result != null) {
                try {
                    result.close();
                } catch (QueryEvaluationException e) {
                    log.error("Caught a QueryEvaluationException! Msg: "
                            + e.getMessage());
                }
            }
            try {
                con.close();
            } catch (RepositoryException e) {
                log.error("Caught a RepositoryException! Msg: "
                        + e.getMessage());
            }
        }

        log.info("Number of constructs identified:  " + constructQuery.size());

    }

    public enum FunctionTypes {
        None, getWcsID, Subtract, Join
    }

    /***************************************************************************
     * Convert construct queries into legal SeRQL queries
     *
     * @param queryString
     * @return
     */
    private String convertSWRLQueryToSeasameQuery(String queryString) {

        postProcessFlag = ProcessingTypes.NONE;

        Pattern stringPattern = Pattern.compile("xs:string\\(([^)]+)\\)");

        Pattern dropquotesPattern = Pattern
                .compile("iridl:dropquotes\\(([^)]+)\\)");
        Pattern minusPattern = Pattern.compile("MINUS.*( using)?");

        Pattern rdfCachePattern = Pattern.compile("rdfcache:"+Terms.reTypeToContext);
        Pattern xsd2owlPattern = Pattern
                .compile("xsd2owl:increment\\(([^)]+)\\)");

        String pproces4sub2 = "\\{\\s*\\{(\\w+)\\s*\\}\\s*(.+)\\{(\\w+)\\s*\\}\\s*\\}";
        Pattern rproces4psub2 = Pattern.compile(pproces4sub2);

        String processedQueryString = queryString;
        log.info("Original construct: " + queryString);
        Matcher mreifStr = rproces4psub2.matcher(processedQueryString);

        Boolean hasReified = false;

        if (mreifStr.find()) {
            String reifstr = " {} rdf:type {rdf:Statement} ; "
                    + " rdf:subject {" + mreifStr.group(1) + "} ;"
                    + " rdf:predicate {" + mreifStr.group(2) + "} ;"
                    + " rdf:object {" + mreifStr.group(3) + "} ;";

            processedQueryString = mreifStr.replaceFirst(reifstr);

            hasReified = true;
            // log.info("query string has reified statements = " + hasReified);
        }

        Matcher stringMatcher = stringPattern.matcher(processedQueryString); // xs:string

        Matcher dropquotesMatcher = dropquotesPattern
                .matcher(processedQueryString); // iridl:dropquotes

        Matcher rdfcacheMatcher = rdfCachePattern.matcher(processedQueryString); // rdfcache:retypeTo

        Matcher xsd2owlMatcher = xsd2owlPattern.matcher(processedQueryString); // xsdToOwl:increment

        //Pattern functionPattern = Pattern
        //        .compile("(([a-z]+):([A-Za-z]+)\\(([^)]+)\\))");// fn:name(abc)
        Pattern comma = Pattern.compile(",");


        //Pattern p_fn = Pattern.compile("(([a-z]+):([A-Za-z]+)\\(([^)]+)\\))");

        Pattern p_fn_className = Pattern.compile("(([a-z]+):([A-Za-z]+)\\(([^)]+)\\)).+using namespace.+\\2 *= *<import:([^#]+)#>",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);

        Matcher functionMatcher = p_fn_className.matcher(processedQueryString);

        //String expand = "";
        if (stringMatcher.find()) {
            postProcessFlag = ProcessingTypes.xsString;
            String vname = stringMatcher.group(1);
            processedQueryString = stringMatcher.replaceAll(vname);
            log.info("Will postprocess xs:string(" + vname + ")");

        } else if (dropquotesMatcher.find()) {
            postProcessFlag = ProcessingTypes.DropQuotes;
            String vname = dropquotesMatcher.group(1);
            processedQueryString = dropquotesMatcher.replaceAll(vname);
            Matcher m23 = minusPattern.matcher(processedQueryString);
            String vname2 = m23.group(1);
            processedQueryString = m23.replaceFirst(vname2);
            log.info("Will postprocess iridl:dropquotes(" + vname + ")");

        } else if (rdfcacheMatcher.find()) {
            postProcessFlag = ProcessingTypes.RetypeTo;
            log.info("Will postprocess rdfcache:"+Terms.reTypeToContext);

        } else if (xsd2owlMatcher.find()) {
            postProcessFlag = ProcessingTypes.Increment;
            String vname = xsd2owlMatcher.group(1);

            processedQueryString = xsd2owlMatcher.replaceAll(vname);

            // log.info("processedQueryString = " + processedQueryString);

        }
        else if (functionMatcher.find()) {
            functionMatcher.reset(); //reset the matcher
            String fullyQualifiedFunctionName;
            while (functionMatcher.find()) {
                String expand = "";
                String rdfFunctionName = functionMatcher.group(3);
                String rdfClassName = functionMatcher.group(5);


                fullyQualifiedFunctionName = rdfClassName + "#" + rdfFunctionName;

                log.debug("fullyQualifiedFunctionName = " + fullyQualifiedFunctionName); // full name of the function
                log.debug("class_name = " + rdfClassName); // class name of the function

                Method myFunction = RepositoryUtility.getMethodForFunction(rdfClassName, rdfFunctionName);

                if (myFunction != null) {
                    postProcessFlag = ProcessingTypes.Function;
                }

                //String[] splittedStr = comma.split(functionMatcher.group(4));
                CSVSplitter splitter = new CSVSplitter();
                String[] splittedStr = splitter.split(functionMatcher.group(4));

                int i = 0;
                String fn = functionMatcher.group(2);
                String functionName = functionMatcher.group(3);

                expand += "}  <"+ Terms.functionsContextUri +"> {" + fn + ":" + functionName
                        + "} ; <"+ Terms.listContextUri +"> {} rdf:first {";
                for (String element : splittedStr) {
                    i++;
                    if (i < splittedStr.length) {
                        if(!element.equals(",")){
                        expand += element + "} ; rdf:rest {} rdf:first {";
                        }else{
                            expand += element;
                        }
                        log.info("element " + i + " = " + element);
                    } else {
                        expand += element + "} ; rdf:rest {rdf:nil";
                        log.info("element " + i + " = " + element);
                    }
                    log.info("Will postprocess fn:" + functionMatcher.group(3));
                }



                processedQueryString = processedQueryString.substring(0, functionMatcher.start(1)) + expand + processedQueryString.substring(functionMatcher.end(1));

                functionMatcher.reset(processedQueryString);


            }
            /***
            while (functionMatcher.find()) {

                String rdfFunctionName = functionMatcher.group(3);
                String rdfClassName = functionMatcher.group(5);


                fullyQualifiedFunctionName = rdfClassName + "#" + rdfFunctionName;

                log.debug("fullyQualifiedFunctionName = " + fullyQualifiedFunctionName); // full name of the function
                log.debug("class_name = " + rdfClassName); // class name of the function

                Method myFunction = getMethodForFunction(rdfClassName, rdfFunctionName);

                if (myFunction != null) {
                    postProcessFlag = ProcessingTypes.Function;
                }

                //String[] splittedStr = comma.split(functionMatcher.group(4));
                CSVSplitter splitter = new CSVSplitter();
                String[] splittedStr = splitter.split(functionMatcher.group(4));

                int i = 0;
                String fn = functionMatcher.group(2);
                String functionName = functionMatcher.group(3);

                expand += "}  <"+RepositoryUtility.functionsContextUri+"> {" + fn + ":" + functionName
                        + "} ; <"+RepositoryUtility.listContextUri+"> {} rdf:first {";
                for (String element : splittedStr) {
                    i++;
                    if (i < splittedStr.length) {
                        if(!element.equals(",")){
                        expand += element + "} ; rdf:rest {} rdf:first {";
                        }else{
                            expand += element;
                        }
                        log.info("element " + i + " = " + element);
                    } else {
                        expand += element + "} ; rdf:rest {rdf:nil";
                        log.info("element " + i + " = " + element);
                    }
                    log.info("Will postprocess fn:" + functionMatcher.group(3));
                }



                processedQueryString = processedQueryString.substring(0, functionMatcher.start(1)) + expand + processedQueryString.substring(functionMatcher.end(1));

                functionMatcher.reset(processedQueryString);


            }*/

        }

        log.info("Processed construct: " + processedQueryString);
        return processedQueryString;

    }

    /***************************************************************************
     * DropQuotes
     *
     * @param graphResult
     * @param creatValue
     * @param Added
     * @param toAdd
     * @param con
     * @param context
     * @throws QueryEvaluationException
     * @throws RepositoryException
     */
    private static void process_DropQuotes(GraphQueryResult graphResult,
            ValueFactory creatValue, Vector<Statement> Added,
            Vector<Statement> toAdd, RepositoryConnection con,
            Resource[] context) throws QueryEvaluationException,
            RepositoryException {

        // pproces2 =\"\\\"([^\\]+)\\\"\"(\^\^[^>]+>)? \.
        String pproces2 = "\\\"\\\\\\\"([^\\\\]+)\\\\\\\"\\\"(\\^\\^[^>]+>)? \\.";
        Pattern rproces2 = Pattern.compile(pproces2);

        while (graphResult.hasNext()) {
            Statement st = graphResult.next();

            Value obj = st.getObject();
            URI prd = st.getPredicate();
            Resource sbj = st.getSubject();
            String statementStr = obj.toString();
            String newStatementStr = "";

            Matcher m = rproces2.matcher(statementStr);
            String vname = m.group(1);
            statementStr = m.replaceAll('"' + vname + '"'
                    + "^^<http://www.w3.org/2001/XMLSchema#string> .");

            String patternBn = "^_:";
            Pattern bn = Pattern.compile(patternBn);

            String sbjStr = sbj.toString();
            Matcher msbjStr = bn.matcher(sbjStr);
            if (msbjStr.find()) {

                // log.debug("Skipping blank node "+sbjStr);
            } else {
                newStatementStr = statementStr;

            }
            statementStr = newStatementStr;

            Value stStr = creatValue.createLiteral(statementStr);
            Statement stToAdd = new StatementImpl(sbj, prd, stStr);

            toAdd.add(stToAdd);
            Added.add(stToAdd);
            con.add(stToAdd, context); // add process_DropQuotes created st

        }
        // log.debug("After processing dropQuotes:\n " +
        // opendap.coreServlet.Util.getMemoryReport());

    }

    /***************************************************************************
     * cast type
     *
     * @param graphResult
     * @param creatValue
     * @param Added
     * @param toAdd
     * @param con
     * @param context
     * @throws QueryEvaluationException
     * @throws RepositoryException
     */

    private void process_RetypeTo(GraphQueryResult graphResult,
            ValueFactory creatValue, Vector<Statement> Added,
            Vector<Statement> toAdd, RepositoryConnection con,
            Resource[] context) throws QueryEvaluationException,
            RepositoryException {

        // pproces3 =\"\\\"([^\\]+)\\\"\"\^\^
        String pproces3 = "\\\"\\\\\\\"([^\\\\]+)\\\\\\\"\\\"\\^\\^";
        Pattern rproces3 = Pattern.compile(pproces3);
        String pproces3sub = "(.+)";
        Pattern rproces3sub = Pattern.compile(pproces3sub);

        String pproces3subsub1 = "<"+ Terms.reTypeToContextUri +"> <([^>]+)>";
        String pproces3subsub2 = "([^ ]+) <http://www.w3.org/1999/02/22-rdf-syntax-ns#value> (\"(.+)\")\\^\\^";

        Pattern rproces3subsub1 = Pattern.compile(pproces3subsub1);
        Pattern rproces3subsub2 = Pattern.compile(pproces3subsub2);

        while (graphResult.hasNext()) {
            Statement st = graphResult.next();

            Value obj = st.getObject();
            URI prd = st.getPredicate();
            Resource sbj = st.getSubject();
            String statementStr = obj.toString();
            Matcher mproces3 = rproces3.matcher(statementStr);

            String replace = mproces3.group(1) + '"' + "^^";
            statementStr = mproces3.replaceAll(replace);

            Matcher mproces3sub = rproces3sub.matcher(statementStr);

            String newStStr = statementStr;
            String lastline = "";
            String line = "";

            String node = "";
            String value = "";
            String newtype = "";
            String statement = "";
            while (mproces3sub.find()) {
                String lastline2 = lastline;
                lastline = line;
                line = mproces3sub.group(1);

                Matcher msub1 = rproces3subsub1.matcher(line);
                if (msub1.find()) {
                    newtype = msub1.group(1);
                    Matcher msub2 = rproces3subsub2.matcher(lastline);
                    if (msub2.find()) {
                        node = msub2.group(1);
                        value = msub2.group(3);
                    }
                    String psub3 = "(.+) " + node + " .";
                    Pattern cpsub3 = Pattern.compile(psub3);
                    Matcher msub3 = cpsub3.matcher(lastline2);
                    if (msub3.find()) {
                        statement = msub3.group(1);
                    }
                    newStStr = statement + " " + '"' + value + '"' + "^^<"
                            + newtype + "> .\n";
                }
            }

            Value stStr = creatValue.createLiteral(newStStr);
            Statement stToAdd = new StatementImpl(sbj, prd, stStr);
            log.debug("original st=" + st.toString());
            log.debug("new stToAdd=" + stToAdd.toString());
            log.debug("In postprocess3");

            toAdd.add(stToAdd);
            Added.add(stToAdd);
            con.add(stToAdd, context);// add process_RetypeTo created st

        }
        // log.debug("After processing RetypeTo:\n " +
        // opendap.coreServlet.Util.getMemoryReport());

    }

    /***************************************************************************
     * Increment numbers
     * @todo WHAT DOES THIS METHOD ACTUALLY DO?
     *
     * @param graphResult
     * @param creatValue
     * @param Added
     * @param toAdd
     * @param con
     * @param context
     * @throws QueryEvaluationException
     * @throws RepositoryException
     */
    private void process_Increment(GraphQueryResult graphResult,
            ValueFactory creatValue, Vector<Statement> Added,
            Vector<Statement> toAdd, RepositoryConnection con,
            Resource[] context) throws QueryEvaluationException,
            RepositoryException {

        String pproces4 = "(.+)";
        Pattern rproces4 = Pattern.compile(pproces4);

        // String pproces4sub ="(.+)\"(\\d+)\"(.+)";
        String pproces4sub = "\"(\\d+)\"";
        // pproces4= \"(\d+)\"
        // String pproces4sub ="\\\"(\\d+)\\\"";
        Pattern rproces4psub = Pattern.compile(pproces4sub);

        String pproces4sub2 = "\\{\\s*\\{(\\w+)\\s*\\}\\s*(.+)\\{(\\w+)\\s*\\}\\s*\\}";
        Pattern rproces4psub2 = Pattern.compile(pproces4sub2);

        while (graphResult.hasNext()) {
            Statement st = graphResult.next();

            Value obj = st.getObject();
            URI prd = st.getPredicate();
            Resource sbj = st.getSubject();
            String statementStr = obj.toString();

            String numincrStr = ""; // after increment

            Matcher mproces4 = rproces4.matcher(statementStr);
            if (mproces4.find()) {
                statementStr = mproces4.group(1);
                Matcher mproces4sub = rproces4psub.matcher(statementStr);

                if (mproces4sub.find()) { // find number, do increment
                    int numincr = Integer.parseInt(mproces4sub.group(1));
                    // log.debug("before increment numincr = " +numincr);
                    numincr++;

                    numincrStr = Integer.toString(numincr);
                    // log.debug("after increment numincrStr = " +numincrStr);

                    statementStr = numincrStr;

                    Value stStr = creatValue.createLiteral(statementStr);
                    Statement stToAdd = new StatementImpl(sbj, prd, stStr);
                    st = stToAdd;
                }
                // log.debug("new st = "+st.toString());

                toAdd.add(st);
                Added.add(st);
                con.add(st, context);// add st with incremented number
                // log.debug("Increment added new tatement stToAdd= "
                // + st.toString());

            } else {
                toAdd.add(st);
                Added.add(st);
                con.add(st, context);// add st without increment (not a
                // number)
                // log.debug("Increment added original tatement st= "
                // + st.toString());
            }

        } // while (graphResult.hasNext())

    }

    /***************************************************************************
     * process fn created statements
     *
     * @param graphResult
     * @param creatValue
     * @param Added
     * @param toAdd
     * @param con
     * @param context
     * @throws QueryEvaluationException
     * @throws RepositoryException
     */
    private void process_fn(GraphQueryResult graphResult,
                             ValueFactory creatValue, Vector<Statement> Added,
                             Vector<Statement> toAdd, RepositoryConnection con,
                             Resource[] context) throws QueryEvaluationException,
            RepositoryException {

        log.debug("Processing fn statements.");


        URI rdffirst = creatValue.createURI("http://www.w3.org/1999/02/22-rdf-syntax-ns#first");
        URI rdfrest = creatValue.createURI("http://www.w3.org/1999/02/22-rdf-syntax-ns#rest");
        URI endList = creatValue.createURI("http://www.w3.org/1999/02/22-rdf-syntax-ns#nil");
        URI myfn = creatValue.createURI(Terms.functionsContextUri);
        URI myfnlist = creatValue.createURI(Terms.listContextUri);

        FunctionTypes functionTypeFlag = FunctionTypes.None;
        Value objLastSt = null;

        URI prdLastSt = null;
        Resource sbjLastSt = null;

        Statement oldSt = null;
        while (graphResult.hasNext()) {
            Statement st = graphResult.next();
            Statement newSt = null;

            Value obj = st.getObject();
            Value listnode = null;

            URI prd = st.getPredicate();
            Resource sbj = st.getSubject();


            URI targetPrd = null;

            Resource targetSbj = null;


            Method func = null;


            if (prd.equals(myfn)) {
                targetSbj = sbjLastSt;
                targetPrd = prdLastSt;


                String className; // class name
                String fnName; // function name
                //if (prd.equals(myfn) && isSbjBn) {
                if (prd.equals(myfn)) {
                    String functionImport = obj.stringValue();
                    int indexOfLastPoundSign = functionImport.lastIndexOf("#");

                    className = functionImport.substring("import:".length(), indexOfLastPoundSign);
                    fnName = functionImport.substring(indexOfLastPoundSign + 1);

                    func = RepositoryUtility.getMethodForFunction(className, fnName);

                }
                Boolean isEndList = endList.equals(obj);
                List<String> rdfList = new ArrayList<String>();
                int statementNbr = 1;
                while (graphResult.hasNext() && !isEndList) {
                    st = graphResult.next();
                    statementNbr++;
                    // log.debug("Current statement " + statementNbr + ": " +
                    // st);
                    obj = st.getObject();
                    prd = st.getPredicate();
                    sbj = st.getSubject();
                    //mbnode = bnode.matcher(sbj.toString());
                    //log.debug(" sbjLastSt = " + targetSbj );
                    //log.debug(" prdLastSt = " + targetPrd );

                    //log.debug(" sbj = " + sbj );
                    //log.debug(" prd = " + prd );
                    //log.debug(" obj = " + obj );
                    if (myfnlist.equals(prd)) {
                        listnode = obj;
                    } else if (listnode.equals(sbj) && rdffirst.equals(prd)) {
                        String elementValue = obj.stringValue();
                        rdfList.add(elementValue);
                    } else if (listnode.equals(sbj) && rdfrest.equals(prd)) {
                        listnode = obj;
                        isEndList = endList.equals(obj);
                    }
                }

                if (func != null) {
                    Value stObj = null;
                    try {

                        // We can pass null here:
                        stObj = (Value) func.invoke(null, rdfList, creatValue);
                        //  because we know that the method that is being
                        // invoked is static, so we don't need an
                        // instance of class to invoke the method from.


                        newSt = new StatementImpl(targetSbj, targetPrd, stObj);
                    } catch (IllegalAccessException e) {
                        log.error("Unable to invoke processing function "
                                + func.getName()
                                + "' Caught IllegalAccessException, msg: "
                                + e.getMessage());
                    } catch (InvocationTargetException e) {
                        log.error("Unable to invoke processing function "
                                + func.getName()
                                + "' Caught InvocationTargetException, msg: "
                                + e.getMessage());
                    }
                } else {
                    log.warn("Process Function failed: No processing function found.");
                }

            } //if (prd.equals(myfn))

            objLastSt = st.getObject();
            prdLastSt = st.getPredicate();
            sbjLastSt = st.getSubject();

            if (newSt != null) {
                log.debug("new st to add = " + newSt.toString());
                st = newSt;
                oldSt = null;
            }

            if (oldSt != null) {
                toAdd.add(oldSt);
                Added.add(oldSt);
                con.add(oldSt, context); // add fn created new st
                log.debug("process_fn add context: " + context[0].toString());
            }
            oldSt = st;

        } // while (graphResult.hasNext())
        if (oldSt != null) {
            toAdd.add(oldSt);
            Added.add(oldSt);
            con.add(oldSt, context); // add fn created new st
            log.debug("process_fn add context: " + context[0].toString());
        }

        log.debug("After processing fn: " + toAdd.size()
                + " statements are added.\n ");
    }

}