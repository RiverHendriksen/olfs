package opendap.threddsHandler;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import opendap.bes.BadConfigurationException;
import opendap.bes.BesDapDispatcher;
import opendap.bes.Version;
import opendap.bes.dap2Responders.BesApi;
import opendap.dap.Dap2Service;
import opendap.namespaces.THREDDS;
import opendap.ppt.PPTException;
import opendap.services.Service;
import opendap.services.ServicesRegistry;
import opendap.viewers.NcWmsService;
import opendap.viewers.WebServiceHandler;
import opendap.xml.Transformer;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jdom.transform.JDOMSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Vector;

/**
 * Created by ndp on 4/13/15.
 */
public class DatasetScan  extends Dataset {

    private Logger _log;

    private BesApi _besApi;

    private String _catalogUrlPrefix;
    private String _besCatalogToThreddsCatalogTransformFilename;

    private Filter _filter;
    private Namer  _namer;

    private Vector<Proxy> _proxies;




    private Element _addProxies;



    private Element _addTimeCoverage;


    public DatasetScan(String catalogUrlPrefix, Element datasetScan, String besCatalogToThreddsCatalogTransformFilename, BesApi besApi)throws BadConfigurationException {
        super(datasetScan);
        _log = LoggerFactory.getLogger(this.getClass());
        _besApi = besApi;
        _besCatalogToThreddsCatalogTransformFilename = besCatalogToThreddsCatalogTransformFilename;
        _catalogUrlPrefix = catalogUrlPrefix;

        _filter = new Filter(getFilter());

        _proxies = getProxies();



    }

    public String getPath(){
        return _sourceDataset.getAttributeValue("path");
    }

    public String getLocation(){
        return _sourceDataset.getAttributeValue("location");
    }

    public Element getNamer(){
        return getCopy(THREDDS.NAMER, THREDDS.NS);
    }

    public Element getFilter(){
        return getCopy(THREDDS.FILTER, THREDDS.NS);
    }

    public boolean increasingSort(){
        boolean ascending = true;
        Element sortElement  = _sourceDataset.getChild(THREDDS.SORT,THREDDS.NS);
        if(sortElement!=null){
            Element lexigraphicByNameElement  = sortElement.getChild(THREDDS.LEXIGRAPHIC_BY_NAME,THREDDS.NS);
            if(lexigraphicByNameElement!=null){
                String increasing  = lexigraphicByNameElement.getAttributeValue(THREDDS.INCREASING);
                if(increasing!=null)
                    ascending = Boolean.parseBoolean(increasing);
            }
        }
        return ascending;

    }
      /*
    public Element getSort(){
        return getCopy(THREDDS.SORT, THREDDS.NS);
    }
    */


    public Vector<Proxy> getProxies(){

        Vector<Proxy> proxies = new Vector<>();

        Element addProxiesElement =  getCopy(THREDDS.ADD_PROXIES, THREDDS.NS);

        if(addProxiesElement==null)
            return null;

        List<Element> proxiesList = addProxiesElement.getChildren();

        for(Element proxy : proxiesList){
            if(proxy.getName().equals(THREDDS.SIMPLE_LATEST)){
                proxies.add(new SimpleLatest(proxy));
            }
            else if(proxy.getName().equals(THREDDS.LATEST_COMPLETE)){
                proxies.add(new LatestComplete(proxy));
            }

            // Note that we ignore anything we don't have code to handle...
        }



        return proxies;


    }

    public Element getAddTimeCoverage(){
        return getCopy(THREDDS.ADD_TIME_COVERAGE, THREDDS.NS);
    }

    private Element getCopy(String name, Namespace ns){
        Element e = _sourceDataset.getChild(name , ns);

        if(e==null)
            return null;


        return (Element) e.clone();

    }




    private String getUrlPrefix() {


        return _catalogUrlPrefix + getPath();

    }

    public boolean matches(String catalogKey){

        String urlPrefix = getUrlPrefix();

        if(catalogKey.startsWith(urlPrefix)){
            Element filter = getFilter();

            if(filter != null){
                _log.error("matches() - Sorry! The filter element is not yet supported.");
            }

            return true;
        }

        return false;


    }


    public Catalog getCatalog(String catalogKey) throws JDOMException, BadConfigurationException, PPTException, IOException, SaxonApiException {


        if(!matches(catalogKey))
            return null;

        XMLOutputter xmlo = new XMLOutputter(Format.getPrettyFormat());

        String catalogPath = catalogKey;
        if(!catalogPath.endsWith("/")){
            int lastSlash = catalogPath.lastIndexOf("/");
            if(lastSlash>0) {
                catalogPath = catalogPath.substring(0, catalogPath.lastIndexOf("/"));
            }
            else {

            }
        }



        String besCatalogResourceId = catalogPath;



        if(besCatalogResourceId.startsWith(getUrlPrefix())){
            besCatalogResourceId = besCatalogResourceId.substring(getUrlPrefix().length());
        }




        /*
        Replaced by catalogPath computation above

        if(besCatalogResourceId.endsWith(CatalogManager.DEFAULT_CATALOG_NAME)){
            besCatalogResourceId = besCatalogResourceId.substring(0,besCatalogResourceId.lastIndexOf(CatalogManager.DEFAULT_CATALOG_NAME));
        }
         */






        while(besCatalogResourceId.startsWith("/") && besCatalogResourceId.length()>1)
            besCatalogResourceId = besCatalogResourceId.substring(1);


        String location = getLocation();
        while(location.endsWith("/") && location.length()>1)
            location  = location.substring(0,location.length()-1);


        besCatalogResourceId = location + "/" + besCatalogResourceId;

        Vector<Element> metadata = getMetadata();


        /*
        Replaced by catalogPath computation above
        if(catalogKey.endsWith(CatalogManager.DEFAULT_CATALOG_NAME))
            catalogKey = catalogKey.substring(0,catalogKey.length() - CatalogManager.DEFAULT_CATALOG_NAME.length());

        */



        Namer namer = new Namer(getNamer(), catalogPath);
        AddTimeCoverage atc = new AddTimeCoverage(getAddTimeCoverage(), catalogPath);


        BesCatalog besCatalog =
                new BesCatalog(
                        _besApi,
                        catalogPath,
                        besCatalogResourceId,
                        _besCatalogToThreddsCatalogTransformFilename,
                        metadata,
                        _filter,
                        increasingSort(),
                        namer,
                        atc,
                        _proxies
                );


        return besCatalog;

    }


}
