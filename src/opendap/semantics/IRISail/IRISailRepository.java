package opendap.semantics.IRISail;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;

import org.openrdf.model.Literal;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.impl.StatementImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.query.BindingSet;
import org.openrdf.query.GraphQuery;
import org.openrdf.query.GraphQueryResult;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFParseException;
import org.openrdf.sail.Sail;
import org.openrdf.model.ValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class extends org.openrdf.repository.sail.SailRepository. It can be used
 * to populate a repository from a file or URL. It inherits all fields and
 * methods from parent class SailRepository. It also has new methods to
 * recursively add imports and seealso statements refered documents. It is RDF
 * schema aware.
 *
 * @author Haibo liu, iri.columbia.edu
 * @version 1.0
 */
public class IRISailRepository extends SailRepository {
    // imports holds contexts (URL of owl, xsd, etc)
    Vector<String> imports;
    HashMap<String, Boolean> downService;
    // constructor
    private Logger log;

    private String resourceDir;
    private String contentDir;

    private Vector<String> constructQuery;
    private HashMap<String, String> constructContext;

    private ProcessingTypes postProcessFlag;
    
    public Boolean hasContext(URI uriContext, RepositoryConnection con)
            throws RepositoryException {
        Boolean existContext = false;
        RepositoryResult<Resource> contextIDs = con.getContextIDs();
        while (contextIDs.hasNext()) {
            Resource contID = contextIDs.next();
            if (contID.equals(uriContext))
                existContext = true;
        }
        contextIDs.close();
        return existContext;
    }

    public IRISailRepository(Sail sail, String resourceDir, String contentDir) {
        super(sail);
        log = LoggerFactory.getLogger(getClass());
        imports = new Vector<String>();
        downService = new HashMap<String, Boolean>();
        this.resourceDir = resourceDir;
        this.contentDir = contentDir;

        constructQuery = new Vector<String>();
        constructContext = new HashMap<String, String>();
    }



    /*
     * Run all Construct queries and statement into repository
     */

    public void runConstruct() throws RepositoryException {


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
        int totalStAdded =0 ;  //number of statements added
        
        findConstruct();

        //log.debug("Before running the construct rules:\n " + opendap.coreServlet.Util.getMemoryReport());
        con = this.getConnection();
        //con.setAutoCommit(false); //turn off autocommit
        while (modelChanged && runNbr < runNbrMax) {
            log.debug("AutoCommit is " +con.isAutoCommit()); //check if autocommit
            runNbr++;
            modelChanged = false;
            log.debug("Applying Construct Rules. Beginning Pass #" + runNbr + " \n" +
                    opendap.coreServlet.Util.getMemoryReport());
            int ruleNumber = 0;
            for (String qstring : this.constructQuery) {
                ruleNumber++; 
                queryTimes++;
                ruleStartTime = new Date().getTime();
                int stAdded =0; //track statements added by each rule
                
                Vector<Statement> toAdd = new Vector<Statement>();
                String constructURL = this.constructContext.get(qstring);

                URI uriaddress = new URIImpl(constructURL);
                Resource[] context = new Resource[1];
                context[0] = uriaddress;

                String processedQueryString = convertSWRLQueryToSeasameQuery(qstring);
                //if (postProcessFlag.equals("Join"))
                //        processedQueryString = convertSWRLQueryToSeasameQuery(processedQueryString); //fn:join twice
                log.debug("Source Query String ID: " +constructURL);
                log.debug("Source Query String: " + qstring + "   Processed Query String: " + processedQueryString);

                try {
                    //log.debug("Prior to making new repository connection:\n " + opendap.coreServlet.Util.getMemoryReport());

                   
                    GraphQuery graphQuery = con.prepareGraphQuery(QueryLanguage.SERQL, processedQueryString);

                    log.info("Querying the repository. PASS #" + queryTimes + " (construct rules pass #" + runNbr + ")");
                    
                    graphResult = graphQuery.evaluate();
                    log.info("Completed querying. ");

                    //log.debug("After evaluating construct rules:\n " + opendap.coreServlet.Util.getMemoryReport());


                    log.info("Post processing query result and adding statements ... ");

                    if (graphResult.hasNext()) {
                        modelChanged = true;

                        ValueFactory creatValue = this.getValueFactory();

                        switch (postProcessFlag) {

                            case xsString:
                                process_xsString(graphResult, creatValue, Added, toAdd, con, context);
                                log.debug("After processing xs:string:\n " + opendap.coreServlet.Util.getMemoryReport());
                                break;

                            case DropQuotes:
                                process_DropQuotes(graphResult, creatValue, Added, toAdd, con, context);
                                log.debug("After processing DropQuotes:\n " + opendap.coreServlet.Util.getMemoryReport());
                                break;

                            case RetypeTo:
                                process_RetypeTo(graphResult, creatValue, Added, toAdd, con, context);
                                log.debug("After processing RetypeTo:\n " + opendap.coreServlet.Util.getMemoryReport());
                                break;

                            case Increment:
                                process_Increment(graphResult, creatValue, Added, toAdd, con, context);
                                log.debug("After processing Increment:\n " + opendap.coreServlet.Util.getMemoryReport());
                                break;
                            
                            case Join:
                                process_Join(graphResult, creatValue, Added, toAdd,
                                        con, context);// postpocessing Join
                                break;

                            case NONE:
                            default:
                                log.info("Adding none-postprocess statements ...");
                            int   nonePostprocessSt = 0;  
                            while (graphResult.hasNext()) {
                                    Statement st = graphResult.next();

                                    con.add(st, context);
                                    //log.debug("Added statement = " +st.toString());
                                    toAdd.add(st);
                                    Added.add(st);
                                    nonePostprocessSt++;
                                }
                                log.info("Complete adding "+nonePostprocessSt + " none-postprocess statements");
                                //log.debug("After processing default (NONE) case:\n " + opendap.coreServlet.Util.getMemoryReport());


                                break;
                        }


                        //DropQuotes, RetypeTo, Increment
                        //log.info("Adding statements ...");
                        stAdded =0;
                        if (toAdd != null) {
                            //con.add(toAdd, context);
                            //log.info("Total added " + toAdd.size() + " statements.");
                            for(Statement sttoadd:toAdd){
                                log.debug("Add statement: "+sttoadd.toString());  
                            }
                            stAdded = toAdd.size();
                        } 
                        //else {
                            //log.info("Added 0 statements.");
                        //}
                        

                        /* try {
                            con.close();
                        } catch (RepositoryException e) {
                            log.error("Caught a RepositoryException! Msg: " + e.getMessage());
                        }*/
                    } //if (graphResult != null
                    else {
                        log.debug("No query result!");
                    }
                    
                } catch (QueryEvaluationException e) {
                    log.error("Caught an QueryEvaluationException! Msg: " + e.getMessage());

                } catch (RepositoryException e) {
                    log.error("Caught RepositoryException! Msg: " + e.getMessage());
                } catch (MalformedQueryException e) {
                    log.error("Caught MalformedQueryException! Msg: " + e.getMessage());
                    log.debug("graphqueryString: " + qstring);
                } finally {
                    if (graphResult != null) {
                        try {
                            graphResult.close();
                        } catch (QueryEvaluationException e) {
                            log.error("Caught a QueryEvaluationException! Msg: " + e.getMessage());
                        }
                    }

                }
                /*try {
                    con.close();
                } catch (RepositoryException e) {
                    log.error("Caught a RepositoryException! Msg: " + e.getMessage());
                } */
                //log.debug("Commit, adding "+ stAdded + " statements ..."); 
                //if (totalStAdded > 0) {
                //con.commit(); //force flushing the memory
                //}
                //log.debug("Complete committing! "); 
                
                ruleEndTime = new Date().getTime();
                double ruleTime = (ruleEndTime - ruleStartTime) / 1000.0;
                log.debug("Cnstruct rule " + ruleNumber +" takes " + ruleTime + " seconds in loop " 
                        + runNbr + "added " + stAdded +" statements");
                totalStAdded = totalStAdded + stAdded;
            } //for(String qstring
            log.info("Completed pass " + runNbr + " of Construct evaluation");
            log.info("Queried the repository " + queryTimes + " times");
            //log.debug("Committing...");
            //if (totalStAdded > 0) {
            //con.commit(); //force flushing the memory
            //}

        }

        //con.setAutoCommit(true); //turn off autocommit
        log.debug("AutoCommit is " +con.isAutoCommit()); //check if autocommit
        try {
            con.close();
        } catch (RepositoryException e) {
            log.error("Caught a RepositoryException! Msg: " + e.getMessage());
        }
        endTime = new Date().getTime();
        double totaltime = (endTime - startTime) / 1000.0;
        log.info("In construct for " + totaltime + " seconds");
        log.info("Total number of statements added in construct: " + Added.size() + " \n");
        
    }


    /*
    * Find all Construct queries
    */
    private void findConstruct() {
        TupleQueryResult result = null;
        RepositoryConnection con = null;
        List<String> bindingNames;


        log.debug("Locating Construct rules...");

        try {
            con = this.getConnection();
            String queryString = "SELECT queries, contexts " +
                    "FROM " +
                    "{contexts} rdfcache:serql_text {queries} " +
                    "using namespace " +
                    "rdfcache = <http://iridl.ldeo.columbia.edu/ontologies/rdfcache.owl#>";

            log.debug("queryString: " + queryString);

            TupleQuery tupleQuery = con.prepareTupleQuery(QueryLanguage.SERQL, queryString);

            result = tupleQuery.evaluate();

            if (result != null) {
                bindingNames = result.getBindingNames();
                //log.debug("There are " + bindingNames.size()
                //      + " binding names for 'import'");

                while (result.hasNext()) {
                    BindingSet bindingSet = (BindingSet) result.next();
                    //Value firstValue =bindingSet.getValue((String)bindingNames.get(0));

                    Value firstValue = bindingSet.getValue("queries");
                    if (!constructQuery.contains(firstValue.stringValue())) {
                        constructQuery.add(firstValue.stringValue());
                    }
                    log.debug("Adding construct to import pool: " + firstValue.toString());
                    Value secondValue = bindingSet.getValue("contexts");
                    constructContext.put(firstValue.stringValue(), secondValue.stringValue());
                    //constructContext.put(firstValue.toString(), secondValue.toString());
                    //log.debug("Query: " +firstValue.stringValue());
                    //log.debug("Context: " +secondValue.stringValue());
                }
            } else {
                log.debug("No query result!");
            }
        } catch (QueryEvaluationException e) {
            log.error("Caught an QueryEvaluationException! Msg: " + e.getMessage());

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
                    log.error("Caught a QueryEvaluationException! Msg: " + e.getMessage());
                }
            }
            try {
                con.close();
            } catch (RepositoryException e) {
                log.error("Caught a RepositoryException! Msg: " + e.getMessage());
            }
        }

        log.info("Number of constructs identified:  " + constructQuery.size());

    }

    public enum ProcessingTypes {
        NONE, xsString, DropQuotes, RetypeTo, Increment, Join
    }



    private String convertSWRLQueryToSeasameQuery(String queryString) {


        postProcessFlag = ProcessingTypes.NONE;

        Pattern p12 = Pattern.compile("xs:string\\(([^)]+)\\)");

        Pattern p22 = Pattern.compile("iridl:dropquotes\\(([^)]+)\\)");
        Pattern p23 = Pattern.compile("MINUS.*( using)?");

        Pattern p3 = Pattern.compile("rdfcache:retypeTo");
        Pattern p42 = Pattern.compile("xsd2owl:increment\\(([^)]+)\\)");

        String pproces4sub2 = "\\{\\s*\\{(\\w+)\\s*\\}\\s*(.+)\\{(\\w+)\\s*\\}\\s*\\}";
        Pattern rproces4psub2 = Pattern.compile(pproces4sub2);


        String processedQueryString = queryString;
        //log.info("Construct queryString: " + queryString);
        Matcher mreifStr = rproces4psub2.matcher(processedQueryString);

        Boolean hasReified = false;



        log.debug("");

        if (mreifStr.find()) {
            String reifstr = " {} rdf:type {rdf:Statement} ; " +
                    " rdf:subject {" + mreifStr.group(1) + "} ;" +
                    " rdf:predicate {" + mreifStr.group(2) + "} ;" +
                    " rdf:object {" + mreifStr.group(3) + "} ;";

            processedQueryString = mreifStr.replaceFirst(reifstr);

            hasReified = true;
            //log.info("query string has reified statements = " + hasReified);
        }

        Matcher m12 = p12.matcher(processedQueryString);

        Matcher m22 = p22.matcher(processedQueryString);

        Matcher m3 = p3.matcher(processedQueryString);

        Matcher m42 = p42.matcher(processedQueryString);
        
        Pattern p_join = Pattern.compile("(fn:join\\(([^)]+)\\))");
        Pattern comma = Pattern.compile(",");

        Matcher m_join = p_join.matcher(processedQueryString);
        String expand = "";

        //}
        if (m12.find()) {
            postProcessFlag = ProcessingTypes.xsString;
            String vname = m12.group(1);
            processedQueryString = m12.replaceAll(vname);
            log.info("Will postprocess xs:string(" + vname + ")");

        } else if (m22.find()) {
            postProcessFlag = ProcessingTypes.DropQuotes;
            String vname = m22.group(1);
            processedQueryString = m22.replaceAll(vname);
            Matcher m23 = p23.matcher(processedQueryString);
            String vname2 = m23.group(1);
            processedQueryString = m23.replaceFirst(vname2);
            log.info("Will postprocess iridl:dropquotes(" + vname + ")");

        } else if (m3.find()) {
            postProcessFlag = ProcessingTypes.RetypeTo;
            log.info("Will postprocess rdfcache:retypeTo");

        } else if (m42.find()) {
            postProcessFlag = ProcessingTypes.Increment;
            String vname = m42.group(1);

            processedQueryString = m42.replaceAll(vname);


            //log.info("processedQueryString = " + processedQueryString);

        }else if (m_join.find()) {
            postProcessFlag = ProcessingTypes.Join;
            
            String[] splittedStr = comma.split(m_join.group(2));
            int i = 0;
            expand += "} fn:myfn {fn:join} ; fn:mylist {} rdf:first {";
            for (String element : splittedStr) {
                i++;
                if (i < splittedStr.length) {
                    expand += element + "} ; rdf:rest {} rdf:first {";
                  //  log.info("element " + i + " = " + element);
                } else {
                    expand += element + "} ; rdf:rest {rdf:nil";
                  //  log.info("element " + i + " = " + element);
                }
            }
            // log.info("expand = " + expand);
            processedQueryString = m_join.replaceFirst(expand);
            m_join = p_join.matcher(processedQueryString);
            if(m_join.find()) {
            splittedStr = comma.split(m_join.group(2));
            int j = 0;
            expand = "";
            expand += "} fn:myfn {fn:join} ; fn:mylist {} rdf:first {";
            for (String element : splittedStr) {
                j++;
                if (j < splittedStr.length) {
                    expand += element + "} ; rdf:rest {} rdf:first {";
                  //  log.info("element " + j + " = " + element);
                } else {
                    expand += element + "} ; rdf:rest {rdf:nil";
                  //  log.info("element " + j + " = " + element);
                }
            }
            processedQueryString = m_join.replaceFirst(expand);
            }
            log.info("Will postprocess fn:join");
        }

        return processedQueryString;

    }









    private void process_xsString(GraphQueryResult graphResult,
                                    ValueFactory creatValue,
                                    Vector<Statement> Added,
                                    Vector<Statement> toAdd,
                                    RepositoryConnection con,
                                    Resource[] context)
            throws QueryEvaluationException, RepositoryException {



        // pproces1 = (\"[^"]+\")\s+\.
        String pproces1 = "(\\\"[^\"]+\\\")\\s+\\.";
        // Create a Pattern object
        Pattern rproces1 = Pattern.compile(pproces1);
        // Now create matcher object.
        while (graphResult.hasNext()) {
            Statement st = graphResult.next();

            Value obj = st.getObject();
            URI prd = st.getPredicate();
            Resource sbj = st.getSubject();
            String statementStr = obj.toString();
            Matcher m = rproces1.matcher(statementStr);
            if (m.find()) {
                String vname = m.group(1);
                String replaceStr = vname + "^^<http://www.w3.org/2001/XMLSchema#string> .";
                statementStr = m.replaceAll(replaceStr);
                //log.debug("postprocess1 statementStr=" +statementStr);
                //log.debug("vnam=" +vname);
            }
            Value stStr = creatValue.createLiteral(statementStr);
            Statement stToAdd = new StatementImpl(sbj, prd, stStr);


            toAdd.add(stToAdd);
            Added.add(stToAdd);
            con.add(stToAdd, context);


        }
        log.debug("After processing xs:string:\n " + opendap.coreServlet.Util.getMemoryReport());
    }
    private void process_DropQuotes(GraphQueryResult graphResult,
                                    ValueFactory creatValue,
                                    Vector<Statement> Added,
                                    Vector<Statement> toAdd,
                                    RepositoryConnection con,
                                    Resource[] context)
            throws QueryEvaluationException, RepositoryException {

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
            statementStr = m.replaceAll('"' + vname + '"' + "^^<http://www.w3.org/2001/XMLSchema#string> .");

            String patternBn = "^_:";
            Pattern bn = Pattern.compile(patternBn);

            String sbjStr = sbj.toString();
            Matcher msbjStr = bn.matcher(sbjStr);
            if (msbjStr.find()) {

                //log.debug("Skipping blank node "+sbjStr);
            } else {
                newStatementStr = statementStr;

            }
            statementStr = newStatementStr;

            Value stStr = creatValue.createLiteral(statementStr);
            Statement stToAdd = new StatementImpl(sbj, prd, stStr);

            toAdd.add(stToAdd);
            Added.add(stToAdd);
            con.add(stToAdd, context);

        }
        log.debug("After processing dropQuotes:\n " + opendap.coreServlet.Util.getMemoryReport());

    }



    private void process_RetypeTo(GraphQueryResult graphResult,
                                    ValueFactory creatValue,
                                    Vector<Statement> Added,
                                    Vector<Statement> toAdd,
                                    RepositoryConnection con,
                                    Resource[] context)
            throws QueryEvaluationException, RepositoryException {

        // pproces3 =\"\\\"([^\\]+)\\\"\"\^\^
        String pproces3 = "\\\"\\\\\\\"([^\\\\]+)\\\\\\\"\\\"\\^\\^";
        Pattern rproces3 = Pattern.compile(pproces3);
        String pproces3sub = "(.+)";
        Pattern rproces3sub = Pattern.compile(pproces3sub);

        String pproces3subsub1 = "<http://iridl.ldeo.columbia.edu/ontologies/rdfcache.owl#reTypeTo> <([^>]+)>";
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
                    newStStr = statement + " " + '"' + value + '"' + "^^<" + newtype + "> .\n";
                }
            }

            Value stStr = creatValue.createLiteral(newStStr);
            Statement stToAdd = new StatementImpl(sbj, prd, stStr);
            log.debug("original st=" + st.toString());
            log.debug("new stToAdd=" + stToAdd.toString());
            log.debug("In postprocess3");

            toAdd.add(stToAdd);
            Added.add(stToAdd);
            con.add(stToAdd, context);

        }
        log.debug("After processing RetypeTo:\n " + opendap.coreServlet.Util.getMemoryReport());

    }
    private void process_Increment(GraphQueryResult graphResult,
                                    ValueFactory creatValue,
                                    Vector<Statement> Added,
                                    Vector<Statement> toAdd,
                                    RepositoryConnection con,
                                    Resource[] context)
            throws QueryEvaluationException, RepositoryException {


        String pproces4 = "(.+)";
        Pattern rproces4 = Pattern.compile(pproces4);

        //String pproces4sub ="(.+)\"(\\d+)\"(.+)";
        String pproces4sub = "\"(\\d+)\"";
        //pproces4= \"(\d+)\"
        //String pproces4sub ="\\\"(\\d+)\\\"";
        Pattern rproces4psub = Pattern.compile(pproces4sub);

        String pproces4sub2 = "\\{\\s*\\{(\\w+)\\s*\\}\\s*(.+)\\{(\\w+)\\s*\\}\\s*\\}";
        Pattern rproces4psub2 = Pattern.compile(pproces4sub2);
        
        //con.setAutoCommit(false);
        
        while (graphResult.hasNext()) {
            Statement st = graphResult.next();

            Value obj = st.getObject();
            URI prd = st.getPredicate();
            Resource sbj = st.getSubject();
            String statementStr = obj.toString();

            String numincrStr = "";   //after increment

            Matcher mproces4 = rproces4.matcher(statementStr);
            if (mproces4.find()) {
                statementStr = mproces4.group(1);
                Matcher mproces4sub = rproces4psub.matcher(statementStr);

                if (mproces4sub.find()) { //find number, do increment
                    int numincr = Integer.parseInt(mproces4sub.group(1));
                    //log.debug("before increment numincr = " +numincr);
                    numincr++;

                    numincrStr = Integer.toString(numincr);
                    //log.debug("after increment numincrStr = " +numincrStr);

                    statementStr = numincrStr;

                    Value stStr = creatValue.createLiteral(statementStr);
                    Statement stToAdd = new StatementImpl(sbj, prd, stStr);
                    st = stToAdd;
                }
                //log.debug("new st = "+st.toString());


                toAdd.add(st);
                Added.add(st);
                con.add(st, context);
                //log.debug("Added new tatement stToAdd= " +st.toString());

            } else {
                toAdd.add(st);
                Added.add(st);
                con.add(st, context);
                //log.debug("Added original tatement st= " +st.toString());
            }



        } // while (graphResult.hasNext())
        //con.commit();
        //con.setAutoCommit(true);
        log.debug("After processing Increment:\n " + opendap.coreServlet.Util.getMemoryReport());

    }


    /**
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
    private void process_Join(GraphQueryResult graphResult,
                              ValueFactory creatValue,
                              Vector<Statement> Added,
                              Vector<Statement> toAdd,
                              RepositoryConnection con,
                              Resource[] context)
            throws QueryEvaluationException, RepositoryException {


        log.debug("Processing JOIN statements.");
        
        Pattern http = Pattern.compile("^http://");
        Pattern bnode = Pattern.compile("^_:");
        Pattern endlist = Pattern.compile("#nil");

        while (graphResult.hasNext()) {
            Statement st = graphResult.next();

            log.debug("Current statement: "+st);

            Value obj = st.getObject();

            URI prd = st.getPredicate();
            Resource sbj = st.getSubject();
           
            URI objUri = null;
            URI targetPrd = prd;
            URI sbjUri = null;

            
            Matcher mobjhttp = http.matcher(obj.stringValue());
            if (mobjhttp.find()) {
                objUri = new URIImpl(obj.toString());
                  
            }
            Matcher msbjhttp = http.matcher(sbj.stringValue());
            if (msbjhttp.find()) {
                sbjUri = new URIImpl(sbj.toString());
                 
            }

            Matcher mbnode = bnode.matcher(sbj.toString());
            Resource targetSbj = null;
            Boolean isSbjBn = false;
            isSbjBn = mbnode.find();
            Boolean isObjBn = false;
            Matcher objbnode = bnode.matcher(obj.toString());
            isObjBn = objbnode.find();
            
            if (!isSbjBn && isObjBn) {
       
                targetSbj = sbj;

            
            String targetObj = "";
            String separator = " "; // default
            Matcher mendlist = endlist.matcher(obj.stringValue());
            Boolean isEndList = false;
            isEndList = mendlist.find();
            List<String> rdfList = new ArrayList<String>();
            while (graphResult.hasNext() && !isEndList) {
                st = graphResult.next();
                log.debug("Current statement2: "+st);
                obj = st.getObject();
                prd = st.getPredicate();
                sbj = st.getSubject();
                mbnode = bnode.matcher(sbj.toString());

                
                isSbjBn = mbnode.find();

                if (isSbjBn && prd.getLocalName().equals("first")) {
                    String elementValue = obj.stringValue();
                    
                    rdfList.add(elementValue);
                    
                }
                
                mendlist = endlist.matcher(obj.stringValue());
                isEndList = mendlist.find();
                //if (isSbjBn && isEndList) break; //exit loop
                
            }
            
            int i = 0;
            for (i = 1; i < rdfList.size() - 1; i++) {
                targetObj += rdfList.get(i) + rdfList.get(0); //rdfList.get(0) separator
            }
            
            targetObj += rdfList.get(i);   //last component no separator
            
            Value stObjStr = creatValue.createLiteral(targetObj);
            Statement stToAdd = new StatementImpl(targetSbj, targetPrd,
                    stObjStr);
            st = stToAdd;
            //log.debug("isSbjBn = " +isSbjBn);
            //log.debug("isObjBn= " +isObjBn );
            //log.debug("isEndList= " +isEndList );
            log.debug("new st in Join = " + stToAdd.toString());
            
            }else if (!isSbjBn && !isObjBn) {
                targetSbj = sbj;
                log.debug("original st in Join = " + st.toString());  
            }
            //log.debug("st to add = " + st.toString());
            toAdd.add(st);
            Added.add(st);
            con.add(st, context);
        } // while (graphResult.hasNext())
         log.debug("After processing Join:\n " + opendap.coreServlet.Util.getMemoryReport());

    }



    /**
     * Finds and returns all imports/seeAlso statement in the repository.
     *
     * @return Stack<String>
     */
    public Vector<String> findImports() {
        TupleQueryResult result = null;
        RepositoryConnection con = null;
        List<String> bindingNames;
        Vector<String> importID = new Vector<String>();
        try {
            con = this.getConnection();
            String queryString = "SELECT DISTINCT ontfile " +
                    "FROM " +
                    "{} owl:imports {ontfile}, [ {ontfile} rdfcache:isContainedBy {collection} ] " +
                    "WHERE collection=NULL " +
                    "UNION " +
                    "SELECT DISTINCT ontfile FROM " +
                    "{} rdfs:seeAlso {ontfile}, [ {ontfile} rdfcache:isContainedBy {collection} ] " +
                    "WHERE collection=NULL " +
                    "UNION " +
                    "SELECT DISTINCT ontfile FROM " +
                    "{} rdfcache:isContainedBy {ontfile}, [ {ontfile} rdfcache:isContainedBy {collection} ] " +
                    "WHERE collection=NULL " +
                    "using namespace " +
                    "rdfs = <http://www.w3.org/2000/01/rdf-schema#>, " +
                    "owl = <http://www.w3.org/2002/07/owl#>," +
                    "rdfcache = <http://iridl.ldeo.columbia.edu/ontologies/rdfcache.owl#>";

            /* String queryString = "SELECT DISTINCT ontfile " +
                       "FROM " +
                       "{} owl:imports {ontfile} "+
               "UNION " +
               "SELECT DISTINCT ontfile FROM " +
               "{} rdfs:seeAlso {ontfile} " +
               "using namespace " +
               "rdfs = <http://www.w3.org/2000/01/rdf-schema#>, " +
               "owl = <http://www.w3.org/2002/07/owl#>," +
               "rdfcache = <http://iridl.ldeo.columbia.edu/ontologies/rdfcache.owl#>";
               */
            log.debug("queryString: " + queryString);

            TupleQuery tupleQuery = con.prepareTupleQuery(QueryLanguage.SERQL, queryString);

            result = tupleQuery.evaluate();

            if (result != null) {
                bindingNames = result.getBindingNames();
                //log.debug("There are " + bindingNames.size()
                //		+ " binding names for 'import'");

                while (result.hasNext()) {
                    BindingSet bindingSet = (BindingSet) result.next();
                    //Value firstValue =bindingSet.getValue((String)bindingNames.get(0));

                    Value firstValue = bindingSet.getValue("ontfile");
                    if (!importID.contains(firstValue.stringValue())
                            && !this.downService.containsKey(firstValue.stringValue())) {
                        importID.add(firstValue.stringValue());
                    }
                    log.debug("Add into import pool: " + firstValue.stringValue());

                }
            } else {
                log.debug("No query result!");
            }
        } catch (QueryEvaluationException e) {
            log.error("Caught an QueryEvaluationException! Msg: " + e.getMessage());

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
                    log.error("Caught a QueryEvaluationException! Msg: " + e.getMessage());
                }
            }
            try {
                con.close();
            } catch (RepositoryException e) {
                log.error("Caught a RepositoryException! Msg: " + e.getMessage());
            }
        }

        log.info("Number of imports:  " + importID.size());
        return importID;
    }


    /**
     * Compile and execute a simple transformation that applies a stylesheet to
     * an input stream, and serializing the result to an OutPutStream
     */
    public ByteArrayOutputStream transformXSD(String inURI)
            throws SaxonApiException {
        log.debug("In transformXSD");
        String transformFileName = resourceDir + "xsl/xsd2owl.xsl";

        Processor proc = new Processor(false);
        XsltCompiler comp = proc.newXsltCompiler();
        XsltExecutable exp = comp.compile(new StreamSource(new File(
                transformFileName)));
        // XsltExecutable exp = comp.compile(new StreamSource(new
        // File("/data/benno/xslt/xsd2owl.xsl")));
        XdmNode source = proc.newDocumentBuilder().build(new StreamSource(inURI));
        Serializer out = new Serializer();
        out.setOutputProperty(Serializer.Property.METHOD, "xml");
        out.setOutputProperty(Serializer.Property.INDENT, "yes");
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        out.setOutputStream(outStream);
        XsltTransformer trans = exp.load();
        trans.setInitialContextNode(source);
        trans.setDestination(out);
        trans.transform();
        log.debug("Output written to OutputStream");
        return outStream;
    }

    /**
     * Transforms a schema file into an xml. Compile and execute a simple
     * transformation that applies a stylesheet to an input stream, and
     * serializing the result to a file trans.xml which will be in turn added
     * into repository. This method is called by update.
     *
     * @param inURI
     * @throws SaxonApiException
     */
    public void transformXSD2File(String inURI) throws SaxonApiException {
        log.debug("In transformXSD2File");
        String transformFileName = resourceDir + "xsl/xsd2owl.xsl";

        Processor proc = new Processor(false);
        XsltCompiler comp = proc.newXsltCompiler();
        XsltExecutable exp = comp.compile(new StreamSource(new File(
                transformFileName)));
        // XsltExecutable exp = comp.compile(new StreamSource(new
        // File("/data/benno/xslt/xsd2owl.xsl")));

        XdmNode source = proc.newDocumentBuilder().build(
                new StreamSource(inURI));

        Serializer out = new Serializer();
        out.setOutputProperty(Serializer.Property.METHOD, "xml");
        out.setOutputProperty(Serializer.Property.INDENT, "yes");
        out.setOutputFile(new File("trans.xml"));
        XsltTransformer trans = exp.load();
        trans.setInitialContextNode(source);
        trans.setDestination(out);
        trans.transform();
        log.debug("Output written to trans.xml");
    }

    /**
     * Checks and returns last modified time of an URL (context) via http
     * connection. The input is a string of an URL.
     *
     * @param urlstring
     */

    public String getLTMODContext(String urlstring) {
        String ltmodstr = "";
        try {
            URL myurl = new URL(urlstring);
            HttpURLConnection hc = (HttpURLConnection) myurl.openConnection();
            long ltmod = hc.getLastModified();
            // log.debug("lastModified: "+ltmod);
            Timestamp ltmodsql = new Timestamp(ltmod);
            String ltmodstrraw = ltmodsql.toString();
            ltmodstr = ltmodstrraw.substring(0, 10) + "T"
                    + ltmodstrraw.substring(11, 19) + "Z";
        } catch (MalformedURLException e) {
            log.error("Caught a MalformedQueryException! Msg: " + e.getLocalizedMessage());
        } catch (IOException e) {
            log.error("Caught an IOException! Msg: " + e.getMessage(), e);
        }
        return ltmodstr;
    }

    /**
     * Checks and returns last modified time of a context (URI) via querying
     * against the repository on contexts.
     *
     * @param urlstring
     */
    public String chkLTMODContext(String urlstring) {
        TupleQueryResult result = null;
        String ltmodstr = "";
        URI uriaddress = new URIImpl(urlstring);
        Resource[] context = new Resource[1];
        context[0] = (Resource) uriaddress;
        RepositoryConnection con = null;

        String queryString = "SELECT DISTINCT x, y FROM CONTEXT <" +
                uriaddress +
                "> {x} <http://iridl.ldeo.columbia.edu/ontologies/rdfcache.owl#last_modified> {y} " +
                "where x=<" + uriaddress + ">";

        try {
            con = this.getConnection();

            TupleQuery tupleQuery = con.prepareTupleQuery(QueryLanguage.SERQL, queryString);
            result = tupleQuery.evaluate();

            BindingSet bindingSet;
            Value valueOfY;

            while (result.hasNext()) { // should have only one value
                bindingSet = (BindingSet) result.next();
                Set<String> names = bindingSet.getBindingNames();
               // for (String name : names) {
               //     log.debug("BindingNames: " + name);
               // }
                valueOfY = (Value) bindingSet.getValue("y");
                ltmodstr = valueOfY.stringValue();
                //log.debug("Y:" + valueOfY.stringValue());

            }

        } catch (QueryEvaluationException e) {
            log.error("Caught a QueryEvaluationException! Msg: " + e.getMessage());
        } catch (RepositoryException e) {
            log.error("Caught a RepositoryException! Msg: " + e.getMessage());
        } catch (MalformedQueryException e) {
            log.error("Caught a MalformedQueryException! Msg: " + e.getMessage());
        } finally {
            try {
                result.close();

            } catch (Exception e) {
                log.error("Caught an Exception! Msg: " + e.getMessage());
            }
            try {
                con.close();
            } catch (RepositoryException e) {
                log.error("Caught a RepositoryException! Msg: " + e.getMessage());
            }
        }

        return ltmodstr;
    }

    
    /**
     * Returns a Hash containing last modified time of a context (URI) from the
     * repository.
     */
    public HashMap<String, String> getLMT() {
        TupleQueryResult result = null;
        String ltmodstr = "";
        String idstr = "";
        HashMap<String, String> idltm = new HashMap<String, String>();
        RepositoryConnection con = null;
        String queryString = "SELECT DISTINCT id, lmt " +
                "FROM " +
                "{cd} wcs:Identifier {id}; " +
                "rdfs:isDefinedBy {doc} rdfcache:last_modified {lmt} " +
                "using namespace " +
                "rdfcache = <http://iridl.ldeo.columbia.edu/ontologies/rdfcache.owl#>, " +
                "wcs= <http://www.opengis.net/wcs/1.1#>";

        try {
            con = this.getConnection();

            TupleQuery tupleQuery = con.prepareTupleQuery(QueryLanguage.SERQL, queryString);
            result = tupleQuery.evaluate();

            BindingSet bindingSet;
            Value valueOfID = null;
            Value valueOfLMT;

            while (result.hasNext()) {
                bindingSet = (BindingSet) result.next();
                //Set<String> names = bindingSet.getBindingNames();
                //for (String name : names) {
                //    log.debug("BindingNames: " + name);
                //}
                valueOfLMT = (Value) bindingSet.getValue("lmt");
                ltmodstr = valueOfLMT.stringValue();

                valueOfID = (Value) bindingSet.getValue("id");
                idstr = valueOfID.stringValue();

                idltm.put(idstr, ltmodstr);

                //log.debug("ID:" + valueOfID.stringValue());
                //log.debug("LMT:" + valueOfLMT.stringValue());

            }
        } catch (QueryEvaluationException e) {
            log.error("Caught a QueryEvaluationException! Msg: " + e.getMessage());
        } catch (RepositoryException e) {
            log.error("Caught a RepositoryException! Msg: " + e.getMessage());
        } catch (MalformedQueryException e) {
            log.error("Caught a MalformedQueryException! Msg: " + e.getMessage());
        } catch (Throwable e) {
            log.error(e.getMessage());
        } finally {
            try {
                result.close();
                con.close();
            } catch (QueryEvaluationException e) {
                log.error("Caught a QueryEvaluationException! Msg: " + e.getMessage());
            }
            catch (RepositoryException e) {
                log.error("Caught a RepositoryException! Msg: " + e.getMessage());
            }
        }

        return idltm;
    }

    /*
      * This method prints all statements in the repository.
      */
    public void printRDF() {
        String queryString = "SELECT DISTINCT x, y FROM {x} p {y} ";
        TupleQueryResult result = null;
        Stack<BindingSet> importID = new Stack<BindingSet>();
        RepositoryConnection con = null;
        try {
            con = this.getConnection();

            TupleQuery tupleQuery = con.prepareTupleQuery(
                    QueryLanguage.SERQL, queryString);

            result = tupleQuery.evaluate();

            while (result.hasNext()) {
                BindingSet bindingSet = (BindingSet) result.next();
                importID.push(bindingSet);

                Value valueOfX = (Value) bindingSet.getValue("x");
                Value valueOfY = (Value) bindingSet.getValue("y");
                log.debug("X:" + valueOfX.stringValue());
                log.debug("Y:" + valueOfY.stringValue());
            }
        } catch (QueryEvaluationException e) {
            log.error(e.getMessage());

        } catch (RepositoryException e) {
            log.error("Caught a RepositoryException! Msg: " + e.getMessage());
        } catch (MalformedQueryException e) {
            log.error("Caught a MalformedQueryException! Msg: " + e.getMessage());
        }
        finally {
            try {
                result.close();
                con.close();
            } catch (Exception e) {
                log.error("Caught an Exception! Msg: " + e.getMessage());

            }
        }

        log.info("Number of RDF:  " + importID.size());
    }

    /**
     * This method prints the total number of statements within a context.
     *
     * @param urlstring
     */
    public void printRDFContext(String urlstring) {
        URI uriaddress = new URIImpl(urlstring);
        TupleQueryResult result = null;
        Resource[] context = new Resource[1];
        context[0] = (Resource) uriaddress;
        RepositoryConnection con = null;

        String queryString = "SELECT DISTINCT x, y FROM CONTEXT <" + uriaddress + "> {x} p {y} ";

        try {
            con = this.getConnection();

            TupleQuery tupleQuery = con.prepareTupleQuery(QueryLanguage.SERQL, queryString);

            result = tupleQuery.evaluate();

            log.debug(urlstring + " has Number of RDF? " + con.size(context));
        } catch (RepositoryException e) {
            log.error("Caught a RepositoryException! Msg: " + e.getMessage());
        } catch (QueryEvaluationException e) {
            log.error("Caught a QueryEvaluationException! Msg: " + e.getMessage());
        } catch (MalformedQueryException e) {
            log.error("Caught a MalformedQueryException! Msg: " + e.getMessage());
        }
        finally {
            try {
                result.close();
                con.close();
            } catch (Exception e) {
                log.error("Caught an Exception! Msg: " + e.getMessage());
            }
        }

    }

    /**
     * Print last modified time of a context. This method takes a URL string as
     * a context and prints last modified time of it.
     */
    public void printLTMODContext(String urlstring) {
        URI uriaddress = new URIImpl(urlstring);
        Resource[] context = new Resource[1];
        context[0] = (Resource) uriaddress;
        TupleQueryResult result = null;
        Stack<BindingSet> importID = new Stack<BindingSet>();
        RepositoryConnection con = null;
        String queryString = "SELECT DISTINCT x, y FROM CONTEXT <" +
                uriaddress +
                "> {x} <http://iridl.ldeo.columbia.edu/ontologies/rdfcache.owl#last_modified> {y} " +
                "where x=<" + uriaddress + ">";

        try {
            con = this.getConnection();

            TupleQuery tupleQuery = con.prepareTupleQuery(QueryLanguage.SERQL, queryString);

            result = tupleQuery.evaluate();
            BindingSet bindingSet;
            Value valueOfY;
            Value valueOfX;
            while (result.hasNext()) {
                bindingSet = result.next();
                importID.push(bindingSet);

                valueOfY = bindingSet.getValue("y");
                valueOfX = bindingSet.getValue("x");
                log.info("context: " + valueOfX.stringValue());
                log.info("last modified time: " + valueOfY.stringValue());
            }

        } catch (QueryEvaluationException e) {
            log.error("Caught a QueryEvaluationException! Msg: " + e.getMessage());

        } catch (RepositoryException e) {
            log.error("Caught an RepositoryException! Msg: " + e.getMessage());
        } catch (MalformedQueryException e) {
            log.error("Caught an MalformedQueryException! Msg: " + e.getMessage());
        } finally {
            try {
                result.close();
                con.close();
            } catch (Exception e) {
                log.error("Caught an Exception! Msg: " + e.getMessage());
            }
        }

        //log.debug("Number of LTMOD?  " + importID.size());
    }

    /**
     * Return true if import context is newer.
     *
     * @param importURL
     * @return Boolean
     */
    public Boolean olderContext(String importURL) {
        Boolean oldLMT = false;

        String oldltmod = this.chkLTMODContext(importURL); //LMT from http header

        //String oldltmod = this.chkLMTContext(importURL); // LMT in owl document

        if (oldltmod.isEmpty()) {
            oldLMT = true;
            return oldLMT;
        }
        String oltd = oldltmod.substring(0, 10) + " "
                + oldltmod.substring(11, 19);
        String ltmod = this.getLTMODContext(importURL);

        String ltd = ltmod.substring(0, 10) + " " + ltmod.substring(11, 19);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd hh:mm:ss");
        Date ltdparseDate;
        try {
            ltdparseDate = dateFormat.parse(ltd);

            log.debug("lastmodified " + ltdparseDate.toString());
            Date oldltdparseDate = dateFormat.parse(oltd);
            log.debug("oldlastmodified " + oldltdparseDate.toString());

            if (ltdparseDate.compareTo(oldltdparseDate) > 0) {// if newer context

                log.info("Import context is newer: " + importURL);
                oldLMT = true;
            }
        } catch (ParseException e) {
            log.error("Caught an ParseException! Msg: " + e.getMessage());

        }
        return oldLMT;
    }

    /**
     * Set last_modified_time of the URI in the repository.
     *
     * @param importURL
     */
    public void setLTMODContext(String importURL, RepositoryConnection con) {
        String pred = "http://iridl.ldeo.columbia.edu/ontologies/rdfcache.owl#last_modified";

        if (!this.imports.contains(importURL)) { // not in the repository yet
            log.debug(importURL);
            String ltmod = this.getLTMODContext(importURL);
            log.debug("lastmodified " + ltmod);
            ValueFactory f = this.getValueFactory();
            URI s = f.createURI(importURL);
            URI p = f.createURI(pred);
            URI cont = f.createURI(importURL);
            URI sxd = f.createURI("http://www.w3.org/2001/XMLSchema#dateTime");
            Literal o = f.createLiteral(ltmod, sxd);

            try {
                //RepositoryConnection con;

                //con = this.getConnection();

                con.add((Resource) s, p, (Value) o, (Resource) cont);

                //log.debug("LITERAL " + o);

                //con.close();

            } catch (RepositoryException e) {
                log.error("Caught an RepositoryException! Msg: " + e.getMessage());

            }

        }
    }

    /**
     * Delete last_modified_time of the URI in the repository.
     *
     * @param importURL
     */
    public void deleteLTMODContext(String importURL, RepositoryConnection con) {
        String pred = "http://iridl.ldeo.columbia.edu/ontologies/rdfcache.owl#last_modified";

        if (!this.imports.contains(importURL)) { // not in the repository yet
            //log.debug(importURL);
            String ltmod = this.getLTMODContext(importURL);
            //log.debug("lastmodified " + ltmod);
            ValueFactory f = this.getValueFactory();
            URI s = f.createURI(importURL);
            URI p = f.createURI(pred);
            URI cont = f.createURI(importURL);
            URI sxd = f.createURI("http://www.w3.org/2001/XMLSchema#dateTime");
            Literal o = f.createLiteral(ltmod, sxd);

            try {
                //RepositoryConnection con;

                //con = this.getConnection();

                con.remove((Resource) s, p, (Value) o, (Resource) cont);

                //log.debug("LITERAL " + o);

                //con.close();

            } catch (RepositoryException e) {
                log.error("Caught an RepositoryException! Msg: " + e.getMessage());

            }

        }
    }


    /**
     * Set IsContainedBy statement for the importURI in the repository.
     *
     * @param importURL
     * @param CollectionURL
     */
    public void setIsContainedBy(String importURL, String CollectionURL) {
        String pred = "http://iridl.ldeo.columbia.edu/ontologies/rdfcache.owl#isContainedBy";

        if (!this.imports.contains(importURL)) { // not in the repository yet
            //log.debug(importURL);
            String ltmod = this.getLTMODContext(importURL);
            //log.debug("lastmodified " + ltmod);
            ValueFactory f = this.getValueFactory();
            URI s = f.createURI(importURL);
            URI p = f.createURI(pred);
            URI cont = f.createURI(importURL);
            URI o = f.createURI(CollectionURL);

            try {
                RepositoryConnection con;

                con = this.getConnection();

                con.add((Resource) s, p, (Value) o, (Resource) cont);

                log.debug("Added to the repository " + "<" + s + "> " + "<" + p + "> " + "<" + o + "> ");

                con.close();

            } catch (RepositoryException e) {
                log.error("Caught an RepositoryException! Msg: " + e.getMessage());

            }

        }
    }

    /**
     * Delete last_modified_time of the URI in the repository.
     *
     * @param importURL
     */
    public void deleteIsContainedBy(String importURL, String CollectionURL) {
        String pred = "http://iridl.ldeo.columbia.edu/ontologies/rdfcache.owl#isContainedBy";

        if (!this.imports.contains(importURL)) { // not in the repository yet
            //log.debug(importURL);
            String ltmod = this.getLTMODContext(importURL);
            //log.debug("lastmodified " + ltmod);
            ValueFactory f = this.getValueFactory();
            URI s = f.createURI(importURL);
            URI p = f.createURI(pred);
            URI cont = f.createURI(importURL);
            URI o = f.createURI(CollectionURL);


            try {
                RepositoryConnection con;

                con = this.getConnection();

                con.remove((Resource) s, p, (Value) o, (Resource) cont);

                log.debug("Deleted to the repository " + "<" + s + "> " + "<" + p + "> " + "<" + o + "> ");
                con.close();

            } catch (RepositoryException e) {
                log.error("Caught an RepositoryException! Msg: " + e.getMessage());

            }

        }
    }

    /**
     * Update repository. This method recursively import any imports and seealso
     * owl/rdf/xsd documents refered in an owl/rdf/xsd document. It returns true
     * if update successes. If the remote server is down, the file is not found,
     * or the file is not modified, no update is performed for this URL. If newer
     * update is available, delete the old statements associated with the context.
     * Note, this method should be called after adding a document into the
     * repository.
     *
     * @return Boolean
     */
    public Boolean update() {
        Boolean update = false;
        URI uriaddress;
        long inferStartTime, inferEndTime;
       inferStartTime = new Date().getTime();
        log.info("Finding imports/seeAlso first time ..."); 
        Vector<String> importID = this.findImports();
        String importURL="";
        Vector<String> importInRepository = new Vector<String>();
      //retrieve context
        RepositoryResult<Resource> contextID;
        try {
            RepositoryConnection con = this.getConnection();
            contextID = con.getContextIDs();
            int contextTol = 0;
            if (!contextID.hasNext()) {
                log.warn("No Contexts found!");
            } else {
                while (contextID.hasNext()) {
                    String ctstr = contextID.next().toString();
                    //log.info("Context: " + ctstr);
                    importInRepository.add(ctstr);
                    printLTMODContext(ctstr);
                    contextTol++;
                }
            }
            contextID.close(); //needed to release resources
            //log.info("Found  " + contextTol + " Contexts");
            
            int i = 0;
            int notimport = 0;

            while (importID.size() != 0 && importID.size() > (this.imports.size() + this.downService.size())) {
                log.debug("importID.size=" + importID.size() + " imports.size=" + this.imports.size() +
                        " downService.size=" + this.downService.size());
                notimport = 0;
                while (!importID.isEmpty()) {
                    importURL = importID.remove(0).toString();

                    log.debug("Checking import URL: "+importURL);
                    URL myurl = new URL(importURL);


                    HttpURLConnection hc = (HttpURLConnection) myurl.openConnection();
                    log.debug("Connected to import URL: "+importURL);

                    int rsCode = -1;
                    try {
                        rsCode = hc.getResponseCode();
                    }
                    catch (IOException e){
                        log.error("Unable to get HTTP status code for "+importURL+" Caught IOException! Msg: " + e.getMessage());
                    }
                    log.debug("Got HTTP status code: "+rsCode);

                    if (this.downService.containsValue(importURL)
                            && this.downService.get(importURL)) {
                        log.error("Server error, Skip " + importURL);
                    }
                    else if (rsCode == -1){
                        log.error("Unable to get an HTTP status code for resource "+importURL+" WILL NOT IMPORT!");
                        this.downService.put(importURL, true);
                        
                    }
                    else if (rsCode > 500) { // server error
                        if (rsCode == 503) {
                            log.error("Error 503 Skipping " + importURL);
                            this.downService.put(importURL, true);
                        }
                        else
                            log.error("Server Error? Received HTTP Status code "+rsCode+" for URL: " + importURL);

                    } else if (rsCode == 304) {
                        log.info("Not modified " + importURL);
                        this.downService.put(importURL, true);
                    } else if (rsCode == 404) {
                        log.error("Received HTTP 404 status for resource: "+importURL);
                        this.downService.put(importURL, true);
                    } else {

                        log.debug("Import URL appears valid ( "+importURL+" )");
                        if (this.imports.contains(importURL)) {
                            log.debug("imports has: " + importURL);

                            if (olderContext(importURL)) {// if new update available delete old one

                                log.info("lastmodified is newer than oldlastmodified, deleting the old context!");
                                URI context2remove = new URIImpl(importURL);
                                con.clear((Resource) context2remove);
                                deleteLTMODContext(importURL, con); //delete last modified time for the context
                                //deleteIsContainedBy(importURL, CollectionURL); //need some work here!!!
                                log.info("finished deleting " + importURL);
                                //con.commit(); //force transaction
                                /* leave for adding in next turn
                                URL url = new URL(importURL);
                                uriaddress = new URIImpl(importURL);
                                log.info("Importing "+importURL);
                                con.add(url, importURL, RDFFormat.RDFXML,
                                        (Resource) uriaddress);
                                setLTMODContext(importURL, con); //set last modified time for the context
                                //setIsContainedBy(importURL, CollectionURL); //need some work here!!!
                                log.info("Finished Importing "+importURL);
                                update = true;
                                */
                                importID.add(importURL); //put back into the add list
                            } else {
                                log.info("Skip old URL: " + importURL);
                            }
                        }
                        if (!this.imports.contains(importURL)) { // not in the import list yet
                            //if (olderContext(importURL)) {
                           if (!importInRepository.contains(importURL)){
                            log.debug("Repository does not have: " + importURL);

                            String urlsufix = importURL.substring((importURL.length() - 4), importURL.length());

                            setLTMODContext(importURL, con);
                            if (urlsufix.equals(".owl") || urlsufix.equals(".rdf")) {

                                uriaddress = new URIImpl(importURL);


                                URL url = new URL(importURL);
                                log.info("Importing URL " + url);
                                con.add(url, importURL, RDFFormat.RDFXML, (Resource) uriaddress);
                                setLTMODContext(importURL, con); //set last modified time for the context
                                //setIsContainedBy(importURL, CollectionURL); //need some work here!!!
                                update = true;
                                log.info("Finished importing URL " + url);

                            } else if (importURL.substring((importURL.length() - 4), importURL.length()).equals(".xsd")) {

                                try {
                                    uriaddress = new URIImpl(importURL);

                                    ByteArrayInputStream inStream;
                                    log.info("Transforming URL " + importURL);
                                    inStream = new ByteArrayInputStream(this.transformXSD(importURL).toByteArray());
                                    log.info("Finished transforming URL " + importURL);
                                    log.debug("Importing URL " + importURL);
                                    con.add(inStream, importURL, RDFFormat.RDFXML, (Resource) uriaddress);
                                    setLTMODContext(importURL, con); //set last modified time for the context
                                    //setIsContainedBy(importURL, CollectionURL); //need some work here!!!
                                    update = true;
                                    log.debug("Finished importing URL " + importURL);
                                } catch (SaxonApiException e) {
                                    log.error("Caught an SaxsonException! Msg: " + e.getMessage());
                                }
                            } else {
                                notimport++;
                                log.info("Not importing URL = " + importURL);
                                log.info("Total not imported Nr = " + notimport);
                            }
                           }else{
                               log.info("Repository has: " + importURL); 
                           }
                            this.imports.add(importURL); // Appends the import/seeAlso to the list of finished

                        } // if (! this.imports.contains(importURL))
                    } // if (this.downService.get(importURL))
                    //con.commit();
                }// while (!importID.empty()

                // owlse2.printRDF ();
                // find all import and seealso
                //con.commit();
                i++;
                int findimportNbr = i+1;
                log.info("Finding imports/seeAlso "+findimportNbr +"times ..."); 
                importID = this.findImports();

                
                log.debug("Update times = " + i);

            }// while (importID.size() != this.imports.size()

        } catch (RepositoryException e) {
            log.error("Caught RepositoyException! Msg: " + e.getMessage());

        } catch (MalformedURLException e) {
            log.error("Caught MalformedURLException! Msg: " + e.getMessage());

        } catch (IOException e) {
            log.error("update() - Failed to import "+importURL+" Caught IOException! Msg: " + e.getMessage());

        } catch (RDFParseException e) {
            log.error("Caught RDFParseException! Msg: " + e.getMessage());
        }
        inferEndTime = new Date().getTime();
        double inferTime = (inferEndTime - inferStartTime) / 1000.0;
        log.debug("Import takes " + inferTime + " seconds");
        return update;
    } // public Boolean update

    /**
     * Updates repository from a given file and baseURI. This method takes a
     * local owl/rdf file containing rdf statements and imports as starting
     * point. It recursively add all imports and seealso find in import
     * documents. It returns true if update successes.
     */
    // File infile = new File(file);
    // URI uriaddress = new URIImpl(baseURI);
    public Boolean updateFromFile(File infile, URI uriaddress) {
        Boolean update = false;
        try {

            RepositoryConnection con = this.getConnection();

            con.add(infile, uriaddress.toString(), RDFFormat.RDFXML, (Resource) uriaddress);

            update = true;

            con.close();
        } catch (RepositoryException e) {
            log.error("Caught RepositoryException! Msg: " + e.getMessage());
        } catch (RDFParseException e) {
            log.error("Caught RDFParseException! Msg: " + e.getMessage());
        } catch (IOException e) {
            log.error("updateFromFile() - Failed to add "+uriaddress+" Caught IOException! Msg: " + e.getMessage());
        }

        Vector<String> importID = this.findImports();

        try {
            RepositoryConnection con = this.getConnection();

            while (importID.size() != this.imports.size()) {
                log.info("importID.size=" + importID.size()
                        + " owlse2.imports.size=" + this.imports.size());
                while (!importID.isEmpty()) {
                    String importURL = importID.remove(0).toString();

                    if (!this.imports.contains(importURL)) { // not in the repository yet

                        String urlsufix = importURL.substring((importURL
                                .length() - 4), importURL.length());
                        log.debug(importURL);

                        this.setLTMODContext(importURL, con);
                        if (urlsufix.equals(".owl") || urlsufix.equals(".rdf")) {
                            uriaddress = new URIImpl(importURL);

                            try {
                                URL url = new URL(importURL);
                                log.info("Importing URL " + url);
                                con.add(url, importURL, RDFFormat.RDFXML, (Resource) uriaddress);
                                log.info("Finished importing URL " + url);
                            } catch (Throwable e) {
                                log.error(e.getMessage());
                            }
                        } else if (importURL.substring(
                                (importURL.length() - 4), importURL.length()).equals(".xsd")) {
                            log.info("XSD: " + importURL);
                            try {
                                uriaddress = new URIImpl(importURL);

                                ByteArrayInputStream inStream;
                                log.info("Transforming URL " + importURL);
                                inStream = new ByteArrayInputStream(this.transformXSD(importURL).toByteArray());
                                log.info("Finished transforming URL " + importURL);
                                log.debug("Importing URL " + importURL);
                                con.add(inStream, importURL, RDFFormat.RDFXML, (Resource) uriaddress);
                                update = true;
                                log.debug("Finished importing URL " + importURL);
                            } catch (SaxonApiException e) {
                                log.error("Caught an SaxsonException! Msg: " + e.getMessage());
                            } catch (IOException e) {
                                log.error("updateFromFile() - Failed to add "+uriaddress+" Caught IOException! Msg: " + e.getMessage());

                            } catch (RDFParseException e) {
                                log.error("Caught RDFParseException! Msg: " + e.getMessage());
                            }

                        } else {

                            log.warn("Not importing " + importURL);
                        }
                        this.imports.add(importURL); // Appends the specified element to the end

                    }// if (! owlse2.imports
                }// while (!importID.empty()

                // owlse2.printRDF ();
                // find all import and seealso
                importID = this.findImports();

            }

            log.info("importID.size=" + importID.size()
                    + " owlse2.imports.size=" + this.imports.size());

            con.close();
        } catch (RepositoryException e) {
            log.error("Caught RepositoryException! Msg: " + e.getMessage());

        }

		return update;
	} // public Boolean updateFromFile
}