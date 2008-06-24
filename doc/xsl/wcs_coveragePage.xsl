<?xml version="1.0" encoding="ISO-8859-1"?>
<!DOCTYPE stylesheet [
<!ENTITY NBSP "<xsl:text disable-output-escaping='yes'>&amp;nbsp;</xsl:text>" >
]>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fn="http://www.w3.org/2005/02/xpath-functions"
                xmlns:wcs="http://www.opengis.net/wcs"
                xmlns:gml="http://www.opengis.net/gml"
                >
    <xsl:output method='html' version='1.0' encoding='UTF-8' indent='yes'/>


    <xsl:template match="wcs:CoverageOffering">
        <html>
            <head>
                <link rel='stylesheet' href='/opendap/docs/css/contents.css'
                      type='text/css'/>
                <title>OPeNDAP Hyrax: <xsl:value-of select="wcs:label"/></title>
            </head>





            <body>
                <!-- ****************************************************** -->
                <!--                      PAGE BANNER                       -->
                <!--                                                        -->
                <!--                                                        -->

                <img alt="OPeNDAP Logo" src='/opendap/docs/images/logo.gif'/>
                <h1>
                    WCS - Coverage Offering: <xsl:value-of select="wcs:label"/>
                </h1>

                <b>Name: </b><xsl:value-of select="wcs:name"/><br />
                <b>Label: </b><xsl:value-of select="wcs:label"/><br />
                <xsl:if test="wcs:description">

                    <xsl:choose> <!-- todo: WHY DOESN"T THIS WORK???  It's always false. -->
                        <xsl:when test="starts-with(wcs:description,'http://')">
                            <a href="{wcs:description}"> Description </a>
                        </xsl:when>
                        <xsl:otherwise>
                            <b>Description: </b><xsl:value-of select="wcs:description"/><br />
                        </xsl:otherwise>
                    </xsl:choose>


                </xsl:if>



                <!-- Start of dates section-->
                <xsl:if test="wcs:domainSet/wcs:temporalDomain">

                    <hr size="1" noshade="noshade"/>

                    <!-- ****************************************************** -->
                    <!--                       PAGE BODY                        -->
                    <!--                                                        -->
                    <!--                                                        -->
                    <pre>
                        <table border="0" width="100%">
                            <tr>
                                <th align="left">Date</th>
                                <!-- <th align="center">Description</th> -->
                                <th align="center">Response Links</th>
                            </tr>
                            <tr>
                                <td>
                                    <xsl:if test="/dataset/name!='/'" >
                                        <a href="..">Parent Directory/</a>
                                    </xsl:if>
                                    <xsl:if test="/dataset/@prefix!='/'" >
                                        <xsl:if test="/dataset/name='/'" >
                                            <a href="..">Parent Directory/</a>
                                        </xsl:if>
                                    </xsl:if>
                                </td>
                            </tr>

                            <!-- Process dataset elements (Date links) -->
                            <xsl:for-each select="wcs:domainSet/wcs:temporalDomain/dataset">
                                    <tr>
                                        <td align="left">
                                            <a href="{name}.html">
                                                <xsl:value-of select="name"/>
                                            </a>
                                        </td>
                                        <td align="center">
                                            <xsl:if test="@isData='true'">
                                                <a href="{name}.ddx">ddx</a>
                                                <a href="{name}.dds">dds</a>
                                                <a href="{name}.das">das</a>
                                                <a href="{name}.info">info</a>
                                                <a href="{name}.html">html</a>
                                            </xsl:if>
                                            <xsl:if test="@isData='false'">
                                                &NBSP; - &NBSP; - &NBSP; - &NBSP; - &NBSP; - &NBSP;
                                            </xsl:if>
                                        </td>
                                        <td align="left">
                                            <xsl:for-each select="wcs:lonLatEnvelope/gml:pos">
                                                [<xsl:value-of select="."/>]
                                            </xsl:for-each>
                                        </td>
                                    </tr>
                            </xsl:for-each>
                        </table>
                    </pre>


                </xsl:if>
                <!-- End of dates section-->


                <xsl:if test="not(wcs:domainSet/wcs:temporalDomain)">

                    <a href="dataset.html">HTML Data Request Form</a>
                    <a href="dataset.ddx">DDX</a>
                    <a href="dataset.dds">DDS</a>
                    <a href="dataset.das">DAS</a>
                    <a href="dataset.info">INFO</a>
                    <a href="dataset.html">HTML</a>


                </xsl:if>






                <!-- ****************************************************** -->
                <!--                              FOOTER                    -->
                <!--                                                        -->
                <!--                                                        -->
                <hr size="1" noshade="noshade"/>
                <table width="100%" border="0">
                    <tr>
                        <td>
                            <div class="small" align="left">
                                THREDDS Catalog
                                <a href="/opendap/catalog.html">
                                    HTML
                                </a>
                                &NBSP;
                                <a href="/opendap/catalog.xml">
                                    XML
                                </a>
                            </div>
                        </td>
                        <td>
                            <div class="small" align="right">
                                Hyrax development sponsored by
                                <a href='http://www.nsf.gov/'>NSF</a>
                                ,
                                <a href='http://www.nasa.gov/'>NASA</a>
                                , and
                                <a href='http://www.noaa.gov/'>NOAA</a>
                            </div>
                        </td>
                    </tr>
                </table>

                <!-- ****************************************************** -->
                <!--                                                        -->
                <h3>OPeNDAP Hyrax WCS Gateway

                    <br/>
                    <a href='/opendap/docs/'>Documentation</a>
                </h3>


            </body>
        </html>
    </xsl:template>




</xsl:stylesheet>
