package opendap.wcs.v2_0;

import net.opengis.gml.v_3_2_1.*;
import net.opengis.wcs.v_2_0.CoverageDescriptionType;
import opendap.dap4.*;
import opendap.namespaces.XML;
import opendap.threddsHandler.ThreddsCatalogUtil;
import opendap.wcs.srs.SimpleSrs;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.xml.bind.*;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class DynamicCoverageDescription extends CoverageDescription {
    private Logger _log;
    private Element _myDMR;
    private DynamicService _dynamicService;
    private SimpleSrs _srs;


    public DynamicCoverageDescription() {
        super();
        _log = LoggerFactory.getLogger(getClass());
        _myDMR = null;
    }

    /**
     * Primary constructor for this class
     *
     * @param dmr
     * @throws IOException
     */
    public DynamicCoverageDescription(Element dmr, DynamicService dynamicService) throws IOException, WcsException {
        this();
        _myDMR = dmr;

        if (dynamicService == null)
            throw new WcsException("There must be a DynamicService associated with the coverage!", WcsException.NO_APPLICABLE_CODE);
        _dynamicService = dynamicService;

        String datasetUrl = dmr.getAttributeValue("base", XML.NS);
        setDapDatasetUrl(new URL(datasetUrl));

        ingestDmr(dmr);

        if (_myCD == null) {
            _myCD = new Element("CoverageDescription", WCS.WCS_NS);
            Element coverageId = new Element("CoverageId", WCS.WCS_NS);
            String name = _myDMR.getAttributeValue("name");
            coverageId.setText(name);
            _myCD.addContent(coverageId);
        }


    }

    /**
     * Uses JAXB to build a Dataset object from the passed DMR.
     *
     * @param dmr
     * @return
     * @throws WcsException
     */
    private Dataset buildDataset(Element dmr) throws WcsException {
        try {
            JAXBContext jc = JAXBContext.newInstance(Dataset.class);
            Unmarshaller um = jc.createUnmarshaller();
            XMLOutputter xmlo = new XMLOutputter(Format.getPrettyFormat());
            String dmrXml = xmlo.outputString(dmr);
            InputStream is = new ByteArrayInputStream(dmrXml.getBytes("UTF-8"));
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader xsr = factory.createXMLStreamReader(is);
            XMLReaderWithNamespaceInMyPackageDotInfo xr = new XMLReaderWithNamespaceInMyPackageDotInfo(xsr);
            Dataset dataset = (Dataset) um.unmarshal(xr);
            if (dataset == null) {
                String msg = "JAXB failed to produce a Dataset from the DMR.";
                _log.debug(msg);
                throw new WcsException(msg, WcsException.NO_APPLICABLE_CODE);
            }
            return dataset;
        } catch (JAXBException | UnsupportedEncodingException | XMLStreamException e) {
            StringBuilder sb = new StringBuilder();
            sb.append("Unable to build Dataset instance from JDOM DMR document.");
            sb.append(" Caught ").append(e.getClass().getName());
            sb.append(" Message  ").append(e.getMessage());
            _log.error(sb.toString());
            throw new WcsException(sb.toString(), WcsException.NO_APPLICABLE_CODE);
        }
    }


    /**
     * Class to hold the massive parameter list for building TimePeriodWithEnvelope
     */
    class EwtpParameters{
        String coverageID;
        String northernMostLat;
        String southernMostLat;
        String easternMostLon;
        String westernMostLon;
        String beginDate;
        String beginTime;
        String endDate;
        String endTime;
        long latitudeSize;
        double latitudeResolution;
        long longitudeSize;
        double longitudeResolution;

    }

    /**
     * I added this layer so that we could get a clear idea of all of the searching, QC, and default values
     * that we will need to have for build the EnvelopeWithTimePeriod. Everyone of these has to be checked and QC's
     * @param cd
     * @param dataset
     */
    private void addEnvelopeWithTimePeriod(CoverageDescriptionType cd, Dataset dataset){

        DomainCoordinate lat = getDomainCoordinate("latitude");
        DomainCoordinate lon = getDomainCoordinate("longitude");

        EwtpParameters ewtp = new EwtpParameters();

        // FIXME Everyone of these values in the  EwtpParameters need to be QC'd and moved a default vakue if needed!
        ewtp.northernMostLat = dataset.getValueOfGlobalAttributeWithNameLike("NorthernmostLatitude");
        ewtp.southernMostLat = dataset.getValueOfGlobalAttributeWithNameLike("SouthernmostLatitude");
        ewtp.easternMostLon =  dataset.getValueOfGlobalAttributeWithNameLike("EasternmostLongitude");
        ewtp.westernMostLon = dataset.getValueOfGlobalAttributeWithNameLike("WesternmostLongitude");
        ewtp.beginDate = dataset.getValueOfGlobalAttributeWithNameLike("RangeBeginningDate");
        ewtp.beginTime = dataset.getValueOfGlobalAttributeWithNameLike("RangeBeginningTime");
        ewtp.endDate = dataset.getValueOfGlobalAttributeWithNameLike("RangeEndingDate");
        ewtp.endTime = dataset.getValueOfGlobalAttributeWithNameLike("RangeEndingTime");
        ewtp.latitudeSize = lat.getSize();
        ewtp.longitudeSize = lon.getSize();
        ewtp.latitudeResolution = Double.parseDouble(dataset.getValueOfGlobalAttributeWithNameLike("LatitudeResolution"));
        ewtp.longitudeResolution = Double.parseDouble(dataset.getValueOfGlobalAttributeWithNameLike("LongitudeResolution"));

        constructEnvelopeWithTimePeriod(cd, ewtp);
    }

    private void constructEnvelopeWithTimePeriod(CoverageDescriptionType cd, EwtpParameters ewtpp) {

        // compute the envelope from dataset
        EnvelopeWithTimePeriod etp = new EnvelopeWithTimePeriod();

        etp.setNorthernmostLatitude(ewtpp.northernMostLat);
        etp.setSouthernmostLatitude(ewtpp.southernMostLat);
        etp.setEasternmostLongitude(ewtpp.easternMostLon);
        etp.setWesternmostLongitude(ewtpp.westernMostLon);

        etp.setRangeBeginningDate(ewtpp.beginDate);
        etp.setRangeBeginningTime(ewtpp.beginTime);
        etp.setRangeEndingDate(ewtpp.endDate);
        etp.setRangeEndingTime(ewtpp.endTime);

        _log.debug(etp.toString());

        net.opengis.gml.v_3_2_1.EnvelopeWithTimePeriodType envelope = etp.getEnvelope(_srs);

        net.opengis.gml.v_3_2_1.BoundingShapeType bs = new net.opengis.gml.v_3_2_1.BoundingShapeType();
        net.opengis.gml.v_3_2_1.ObjectFactory gmlFactory = new net.opengis.gml.v_3_2_1.ObjectFactory();
        bs.setEnvelope(gmlFactory.createEnvelopeWithTimePeriod(envelope));

        // Grid Envelope
        net.opengis.gml.v_3_2_1.GridEnvelopeType gridEnvelope = gmlFactory.createGridEnvelopeType();

        ////////////////////////////////////////////////////////////
        // Crucial member variable state setting...
        // Note: The index values for the arrays are 0 based and so the upper index is
        // one less than the size.
        List<BigInteger> upper = Arrays.asList(
                BigInteger.valueOf(ewtpp.latitudeSize - 1),
                BigInteger.valueOf(ewtpp.longitudeSize - 1));
        List<BigInteger> lower = Arrays.asList(BigInteger.ZERO, BigInteger.ZERO);
        gridEnvelope.withHigh(upper).withLow(lower);
        ////////////////////////////////////////////////////////////

        // Create the limits, set the envelope on them.
        GridLimitsType gridLimits = gmlFactory.createGridLimitsType();
        gridLimits.withGridEnvelope(gridEnvelope);

        net.opengis.gml.v_3_2_1.DomainSetType domainSet = new net.opengis.gml.v_3_2_1.DomainSetType();
        net.opengis.gml.v_3_2_1.RectifiedGridType rectifiedGrid = new net.opengis.gml.v_3_2_1.RectifiedGridType();
        rectifiedGrid.setDimension(new BigInteger(this.getDomainCoordinates().size() + ""));

        rectifiedGrid.setId(ewtpp.coverageID);

        //Create the grid envelope for the limits
        rectifiedGrid.setLimits(gridLimits);

        List<String> axisLabels = _srs.getAxisLabelsList();
        rectifiedGrid.setAxisLabels(axisLabels);

        // Create the Origin.
        DirectPositionType position = gmlFactory.createDirectPositionType();
        position.withValue(
                Double.valueOf(etp.getSouthernmostLatitude()),
                Double.valueOf(etp.getWesternmostLongitude()));
        PointType point = gmlFactory.createPointType();
        point.withPos(position);
        point.setId("GridOrigin-" + ewtpp.coverageID);
        point.setSrsName(_srs.getName());
        PointPropertyType origin = gmlFactory.createPointPropertyType();
        origin.withPoint(point);
        rectifiedGrid.setOrigin(origin);

        // Create the offset vector.
        List<VectorType> offsetList = new ArrayList<VectorType>();
        VectorType offset1 = gmlFactory.createVectorType();

        offset1.withValue(ewtpp.latitudeResolution, 0.0);
        offset1.setSrsName(_srs.getName());
        offsetList.add(offset1);
        VectorType offset2 = gmlFactory.createVectorType();

        offset2.withValue(0.0, ewtpp.longitudeResolution);
        offset2.setSrsName(_srs.getName());
        offsetList.add(offset2);
        rectifiedGrid.setOffsetVector(offsetList);

        domainSet.setAbstractGeometry(gmlFactory.createRectifiedGrid(rectifiedGrid));
        cd.setDomainSet(gmlFactory.createDomainSet(domainSet));
        cd.setBoundedBy(bs);
    }


    /**
     * This method examines the variables in the dataset. It determines which variables can be added to the coverage
     * as fields and then adds them to the CoverageDescription
     *
     * @param cd
     * @param dataset
     */
    private void addRange(CoverageDescriptionType cd, Dataset dataset) {
        net.opengis.swecommon.v_2_0.DataRecordPropertyType rangeType = new net.opengis.swecommon.v_2_0.DataRecordPropertyType();
        net.opengis.swecommon.v_2_0.DataRecordType dataRecord = new net.opengis.swecommon.v_2_0.DataRecordType();
        List<net.opengis.swecommon.v_2_0.DataRecordType.Field> fieldList = new ArrayList<>();


        for (Variable var : dataset.getVariables()) {
            if (compareVariableDimensionsWithDataSet(var, dataset)) {
                fieldList.add(getField(var));
            }
        }

        dataRecord.setField(fieldList);
        rangeType.setDataRecord(dataRecord);
        cd.setRangeType(rangeType);

    }

    /**
     * Returns the size of the requested coordinate variable. Since DomainCoordinate may only have a single
     * dimension it get's all exceptiony if they have more.
     *
     * @param dataset
     * @param standard_name The CF standard_name of the coordinate variable;
     * @return
     * @throws WcsException
     */

    public Dimension getDomainCoordinateVariableDimension(Dataset dataset, String standard_name) throws WcsException {

        Variable coordinateVariable = findVariableWithCfStandardName(dataset, standard_name);
        if (coordinateVariable == null)
            return null;

        List<Dim> dims = coordinateVariable.getDims();
        if (dims.size() > 1)
            throw new WcsException("Coordinate variable must have a single dimension. dims: {}", dims.size());

        Dim dim = dims.get(0);
        return dataset.getDimension(dim.getName());
    }

    /**
     * Returns the Dap 4 variable with CF compliant standard name.  The standard name is actually a
     * value of an attribute named standard_name.  This method will lookl for this whether or not
     * the underlying DMR Dataset is CF compliant
     *
     * @param dataset       - a JAXB representation of the DMR response
     * @param standard_name
     * @return opendap.dap4.Variable
     * @throws WcsException throw any exception is handling the dataset
     */
    public Variable findVariableWithCfStandardName(Dataset dataset, String standard_name) throws WcsException {

        if (!dataset.usesCfConventions())
            _log.warn("Dataset does not appear conform to the CF convention. Dataset: {}", this.getDapDatasetUrl());

        // proceed to look for it anyway, returning null if not found
        for (Variable v : dataset.getVariables()) {
            if (Objects.equals(standard_name, v.getAttributeValue("standard_name"))) {
                _log.debug("Found variable with standard name ", standard_name, v.getName());
                return v;
            }
        }
        return null;
    }


    /**
     * @param dataset
     * @return The SRS for the coverage represented by the dataset.
     */
    public SimpleSrs getSRS(Dataset dataset) {
        // TODO Add a bunch of code to evaluate the dataset and figure out the SRS.
        _log.info("Utilizing default SRS for dataset: {}", dataset.getName());
        return _dynamicService.getSrs();
    }


    /**
     * The code builds a DomainCoordinate starting with a default. It examines the dataset and if the DomainCoordinate
     * be located then the Dataset version is used to populate the new DomainCoordinate, otherwise the default values
     * are used to construct the new DomainCoordinate
     *
     * @param defaultCoordinate
     * @param dataset
     * @return
     * @throws BadParameterException
     * @throws WcsException
     */
    private DomainCoordinate getDomainCoordinate(DomainCoordinate defaultCoordinate, Dataset dataset) throws BadParameterException, WcsException {

        DomainCoordinate domainCoordinate;
        String coordinateName = defaultCoordinate.getName();
        Variable coordinateVariable = findVariableWithCfStandardName(dataset, coordinateName);
        if (coordinateVariable != null) {
            String units = coordinateVariable.getAttributeValue("units");
            if (units == null)
                units = defaultCoordinate.getUnits();

            Dimension coordinateDimension = getDomainCoordinateVariableDimension(dataset, coordinateName);

            long size = defaultCoordinate.getSize();
            try {
                size = Long.parseLong(coordinateDimension.getSize());
            } catch (NumberFormatException nfe) {
                _log.warn("Failed to parse Dimension size string: " + coordinateDimension.getSize());
            }

            domainCoordinate = new DomainCoordinate(
                    coordinateName,
                    coordinateVariable.getName(),
                    units,
                    "",
                    size,
                    coordinateName);
        } else {
            domainCoordinate = new DomainCoordinate(defaultCoordinate);
        }

        return domainCoordinate;

    }


    /**
     * Examines the passed Dataset object and determines the DomainCoordinates for the coverage. This acitvity
     * utilizes the DynamicService to determine the domain coordinates and their order.
     * The results are added as state to the object and thus set the stage for later
     * deciding which DAP variables will be fields in the coverage, and later for building functional DAP data requests
     * to service the  WCS GetCoverage  operation for the coverage.
     * <p>
     * The DAP fields in the coverage will have to have the dimensions in the same order as defined
     * the DynamicService instance held by this class.
     *
     * @param dataset
     * @throws WcsException
     */
    private void ingestDomainCoordinates(Dataset dataset) throws WcsException {
        try {
            // Everyone thinks that somehow Time is a "special" coordinate (Oy, still with that) but
            // it's really not, so we handle it like any other coordinate
            // It should be in the list from the DynamicService if there is a time coordinate.
            for (DomainCoordinate defaultCoordinate : _dynamicService.getDomainCoordinates()) {
                DomainCoordinate domainCoordinate = getDomainCoordinate(defaultCoordinate, dataset);
                addDomainCoordinate(domainCoordinate);
            }
        } catch (BadParameterException e) {
            throw new WcsException("Failed to create DomainCoordinate ", WcsException.NO_APPLICABLE_CODE);
        }
    }


    public void ingestSrsFromDataset(SimpleSrs defaultSrs) {
        // TODO We need to examine the Dataset metadata and see if there is something we can use to determine the SRS
        _srs = new SimpleSrs(defaultSrs);
    }


    /**
     * This method uses a DMR to build state into the CoverageDescrption
     *
     * @param dmr
     * @throws WcsException
     */
    private void ingestDmr(Element dmr) throws WcsException {

        Dataset dataset = buildDataset(dmr);
        _log.debug("Marshalling WCS from DMR at Url: {}", dataset.getUrl());

        CoverageDescriptionType cd = new CoverageDescriptionType();
        ingestSrsFromDataset(_dynamicService.getSrs());
        ingestDomainCoordinates(dataset);
        addEnvelopeWithTimePeriod(cd, dataset);
        addRange(cd, dataset);

        hardwireTheCdAndDcdForTesting(dataset.getName(), getDapDatasetUrl(), cd);
    }


    public Element coverageDescriptionType2JDOM(CoverageDescriptionType cd) throws WcsException {

        // Boiler plate JAXB marshaling of Coverage Description object into JDOM

        ////////////////////////////////////////////////////////
        // Since this was generated from third-party XML schema
        // need to bootstrap the JAXBContext
        // from the package name of the generated model
        // or the ObjectFactory class
        // (i.e. just have to know the package: net.opengis.wcs.v_2_0)

        // Required: First, bootstrap context with known WCS package name

        Marshaller jaxbMarshaller;

        try {
            JAXBContext jaxbContext = JAXBContext.newInstance("net.opengis.wcs.v_2_0");
            jaxbMarshaller = jaxbContext.createMarshaller();
        } catch (JAXBException e) {
            String msg = "Failed to get JAXB Marshaller! JAXBException Message: " + e.getMessage();
            _log.error(msg);
            throw new WcsException(msg, WcsException.NO_APPLICABLE_CODE);
        }

        try {

            // optional:  output "pretty printed"
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            // optional: this is a list of the schema definitions.
            jaxbMarshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
                    "http://www.opengis.net/wcs/2.0 http://schemas.opengis.net/wcs/2.0/wcsAll.xsd " +
                            "http://www.opengis.net/gml/3.2 http://schemas.opengis.net/gml/3.2.1/gml.xsd " +
                            "http://www.opengis.net/gmlcov/1.0 http://schemas.opengis.net/gmlcov/1.0/gmlcovAll.xsd " +
                            "http://www.opengis.net/swe/2.0 http://schemas.opengis.net/sweCommon/2.0/swe.xsd");

            // optional:  capture namespaces per MyMapper, instead of ns2, ns8 etc
            //jaxbMarshaller.setProperty("com.sun.xml.internal.bind.namespacePrefixMapper", new MyNamespaceMapper());
            jaxbMarshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", new MyNamespaceMapper());

        } catch (PropertyException e) {
            _log.warn("NON-FATAL ISSUE WARNING: Another JAXB impl (not the reference implementation) is being used" +
                    "...namespace prefixes like wcs, gml will not show up...instead you will ns2, ns8 etc. Message" + e.getMessage());
        }

        //////////////////////////////////////////////////////////////////////////////////////
        // per https://stackoverflow.com/questions/819720/no-xmlrootelement-generated-by-jaxb
        // method#1:  need to wrap CoverageDescription as JAXB element
        // marshal coverage description into console (more specifically, System.out)
        //jaxbMarshaller.marshal(new JAXBElement(new QName("http://www.opengis.net/wcs/2.0", "wcs"), CoverageDescriptionType.class, cd), System.out);

        // TODO: marshal this into the OLFS JDOM object representation of CoverageDescription...more directly

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db;
        try {
            db = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            String msg = "Failed to get DocumentBuilder! ParserConfigurationException Message: " + e.getMessage();
            _log.error(msg);
            throw new WcsException(msg, WcsException.NO_APPLICABLE_CODE);
        }
        Document doc = db.newDocument();

        //////////////////////////////////////////////////////////////////////////////////////////
        // per https://stackoverflow.com/questions/819720/no-xmlrootelement-generated-by-jaxb/
        // method#2: wrap WCS Coverage Description as JAXB Element using Object Factory
        // marshal coverage description into a org.w3c.dom.Document...first

        // ... and then convert the resultant org.w3c.dom.Document to JDOM (1.1.3) ..which is what OLFS runs on
        // (for JDOM 2, the Builder would be org.jdom2.input.DOMBuilder)
        net.opengis.wcs.v_2_0.ObjectFactory wcsObjFactory = new net.opengis.wcs.v_2_0.ObjectFactory();
        try {
            jaxbMarshaller.marshal(wcsObjFactory.createCoverageDescription(cd), doc);
        } catch (JAXBException e) {
            String msg = "Failed to get marshall CoverageDescription! JAXBException Message: " + e.getMessage();
            _log.error(msg);
            throw new WcsException(msg, WcsException.NO_APPLICABLE_CODE);
        }
        org.jdom.input.DOMBuilder jdb = new org.jdom.input.DOMBuilder();
        org.jdom.Document jdoc = jdb.build(doc);

        // gotcha!  This is what integrates into OLFS (mostly).
        // The rest of CoverageDescription object can be derive from whatever has been captured so far
        // or from _myCD (TODO).

        Element cdElement = jdoc.getRootElement();
        cdElement.detach();

        // couple of quick sanity checks
        _log.debug(cdElement.toString());
        Element coverageId = cdElement.getChild("CoverageId", WCS.WCS_NS);
        _log.debug(coverageId.getText());
        return cdElement;
    }


    private boolean compareVariableDimensionsWithDataSet(Variable var, Dataset dataset) {
        boolean flag = true;

        List<Dim> vdims = var.getDims();
        List<Dimension> dimensions = dataset.getDimensions();

        if (vdims == null || dimensions == null || vdims.isEmpty() || dimensions.isEmpty()) {
            return false;
        } else if (vdims.size() == dimensions.size()) {
            _log.debug("Examining dimension of Variable " + var.getName() + " which has same number of dimensions as Dataset, " + vdims.size());
            for (Dim dim : var.getDims()) {
                boolean found = false;
                String dimName = dim.getName();
                if (dimName.charAt(0) == '/') dimName = dimName.substring(1);
                _log.debug("Look at " + var.getName() + " dimension " + dimName + ", assume it is not in dataset to begin with");
                for (Dimension dimension : dataset.getDimensions()) {
                    _log.debug("comparing variable dimension " + dimName + " with Dataset dimension name " + dimension.getName());

                    // probably need a better test
                    if (dimName.equalsIgnoreCase(dimension.getName())) found = true;
                }

                if (found) {
                    _log.debug("Dimension " + dimName + " found in Dataset");
                    continue;
                } else {
                    _log.debug("Dimension " + dimName + " NOT found in DataSet");
                    flag = false;
                    break;
                }

            }
        } else {
            flag = false;
            _log.debug("Variable " + var.getName() + " has " + vdims.size() + " dimensions, while Dataset has " + dimensions.size());
        }

        if (flag) {
            _log.debug("All dimensions in Variable " + var.getName() + " match DataSet, so it will be included in WCS coverage ");
        } else {
            _log.debug("All dimensions in Variable " + var.getName() + " did NOT match DataSet, so it will be NOT included in WCS coverage ");
        }

        return flag;
    }

    /**
     * Generates a DataRecord.Field from Dap4 variable
     *
     * @param var The DAP4 Variable from which to produce a field.
     * @return
     */
    private net.opengis.swecommon.v_2_0.DataRecordType.Field getField(Variable var) {

        net.opengis.swecommon.v_2_0.DataRecordType.Field field =
                new net.opengis.swecommon.v_2_0.DataRecordType.Field();

        field.setName(var.getName());

        net.opengis.swecommon.v_2_0.QuantityType quantity = new net.opengis.swecommon.v_2_0.QuantityType();
        quantity.setDefinition("urn:ogc:def:dataType:OGC:1.1:measure");
        quantity.setDescription(var.getAttributeValue("long_name"));

        net.opengis.swecommon.v_2_0.UnitReference uom = new net.opengis.swecommon.v_2_0.UnitReference();
        uom.setCode(var.getAttributeValue("units"));
        quantity.setUom(uom);

        net.opengis.swecommon.v_2_0.AllowedValuesPropertyType allowedValues = new net.opengis.swecommon.v_2_0.AllowedValuesPropertyType();
        net.opengis.swecommon.v_2_0.AllowedValuesType allowed = new net.opengis.swecommon.v_2_0.AllowedValuesType();
        List<Double> allowedInterval = Arrays.asList(Double.valueOf(var.getAttributeValue("vmin")),
                Double.valueOf(var.getAttributeValue("vmax")));
        List<JAXBElement<List<Double>>> coordinates = new Vector<JAXBElement<List<Double>>>();
        net.opengis.swecommon.v_2_0.ObjectFactory sweFactory = new net.opengis.swecommon.v_2_0.ObjectFactory();
        coordinates.add(sweFactory.createAllowedValuesTypeInterval(allowedInterval));
        allowed.setInterval(coordinates);
        allowedValues.setAllowedValues(allowed);
        quantity.setConstraint(allowedValues);

        field.setAbstractDataComponent(sweFactory.createAbstractDataComponent(quantity));

        /////////////////////////////////////////////////////////////
        // Crucial member variable state setting...
        this.addFieldToDapVarIdAssociation(var.getName(), var.getName());
        /////////////////////////////////////////////////////////////


        return field;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Out friend main() runs a sanity check using a DMR obtained from test.opendap.org
     *
     * @param args Ignored...
     */
    public static void main(String[] args) {
        XMLOutputter xmlo = new XMLOutputter(Format.getPrettyFormat());
        String testDmrUrl = "https://goldsmr4.gesdisc.eosdis.nasa.gov/opendap/MERRA2/M2I1NXASM.5.12.4/1992/01/MERRA2_200.inst1_2d_asm_Nx.19920123.nc4.dmr.xml";

        testDmrUrl = "http://test.opendap.org/opendap/testbed-13/MERRA2_100.statD_2d_slv_Nx.19800101.SUB.nc4.dmr.xml";
        try {
            Element dmrElement = opendap.xml.Util.getDocumentRoot(testDmrUrl);
            dmrElement.detach();

            SimpleSrs defaultSrs = new SimpleSrs("urn:ogc:def:crs:EPSG::4326", "latitude longitude", "deg deg", 2);
            DynamicService ds = new DynamicService();
            ds.setSrs(defaultSrs);

            String s = "time";
            DomainCoordinate dc = new DomainCoordinate(s, s, "minutes since 1980-01-01 00:30:00", "", 1, s);
            ds.setTimeCoordinate(dc);

            s = "latitude";
            dc = new DomainCoordinate(s, s, "deg", "", 361, s);
            ds.setLatitudeCoordinate(dc);

            s = "longitude";
            dc = new DomainCoordinate(s, s, "deg", "", 576, s);
            ds.setLongitudeCoordinate(dc);

            CoverageDescription cd = new DynamicCoverageDescription(dmrElement, ds);

            System.out.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
            System.out.println("RESULT: " + cd.toString());
            xmlo.output(cd.getCoverageDescriptionElement(), System.out);
            System.out.println("");
            System.out.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
            xmlo.output(cd.getCoverageSummary(), System.out);
            System.out.println("");
            System.out.println("- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
        } catch (Throwable t) {
            t.printStackTrace();
        }

    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////


    private void hardwireTheCdAndDcdForTesting(String id,
                                               URL datasetURl,
                                               CoverageDescriptionType cd) throws WcsException {
        cd.setCoverageId(id);
        cd.setId(id);

        ////////////////////////////////////////////////////////////
        // Crucial member variable state setting...
        this.setDapDatasetUrl(datasetURl);
        ////////////////////////////////////////////////////////////


        net.opengis.wcs.v_2_0.ServiceParametersType serviceParameters = new net.opengis.wcs.v_2_0.ServiceParametersType();
        net.opengis.wcs.v_2_0.ObjectFactory wcsFactory = new net.opengis.wcs.v_2_0.ObjectFactory();
        serviceParameters
                .setCoverageSubtype(new QName("http://www.opengis.net/wcs/2.0", "RectifiedGridCoverage", "wcs"));
        serviceParameters.setNativeFormat("application/octet-stream");

        cd.setServiceParameters(serviceParameters);

        _myCD = coverageDescriptionType2JDOM(cd);

    }

}
