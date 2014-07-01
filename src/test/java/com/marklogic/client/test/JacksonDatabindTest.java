/*
 * Copyright 2012-2014 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.marklogic.client.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.geonames.Toponym;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.marklogic.client.document.DocumentWriteSet;
import com.marklogic.client.document.GenericDocumentManager;
import com.marklogic.client.document.JSONDocumentManager;
import com.marklogic.client.document.XMLDocumentManager;
import com.marklogic.client.io.Format;
import com.marklogic.client.io.JacksonPojoHandle;
import com.marklogic.client.query.DeleteQueryDefinition;
import com.marklogic.client.query.QueryManager;
import com.marklogic.client.test.Common;
import com.marklogic.client.test.BulkReadWriteTest;
import com.marklogic.client.test.BulkReadWriteTest.City;
import com.marklogic.client.test.BulkReadWriteTest.CityWriter;

public class JacksonDatabindTest {
    private static final String CITIES_FILE = "cities_above_300K.txt";
    private static final int MAX_TO_WRITE = 10;
    private static final String DIRECTORY = "/databindTest/";

    @BeforeClass
    public static void beforeClass() {
        Common.connect();
        System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "debug");
    }

    @AfterClass
    public static void afterClass() {
        cleanUp();
        Common.release();
    }

    /** Here we're trying to keep it simple and demonstrate how you would use Jackson
     * via JacksonPojoHandle to do the most common-case databinding to serialize your 
     * pojos to json.  To reuse existing code we're letting BulkReadWriteTest load 
     * records from a csv file and populate our City pojos.  We just manage the 
     * serialization and persistence logic.
     **/
    public class JsonCityWriter implements CityWriter {
        private int numCities = 0;
        private JSONDocumentManager docMgr = Common.client.newJSONDocumentManager();
        private DocumentWriteSet writeSet = docMgr.newWriteSet();

        public void addCity(City city) {
            if ( numCities >= MAX_TO_WRITE ) return;
            // instantiate a JacksonPojoHandle ready to serialize this city to json
            JacksonPojoHandle handle = new JacksonPojoHandle(city);
            // demonstrate our ability to set advanced configuration on the mapper
            // in this case, we're saying wrap our serialization with the name of the pojo class
		    handle.getMapper().enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_OBJECT); 
            writeSet.add(DIRECTORY + "/jsonCities/" + city.getGeoNameId() + ".json", handle);
            numCities++;
        }
        public void finishBatch() {
            if ( writeSet.size() > 0 ) {
                docMgr.write(writeSet);
                // while this test is usually just 10 records so no more than one write set,
                // we're ready to do more batches if we want to do performance testing here
                writeSet = docMgr.newWriteSet();
            }
        }
        public void setNumRecords(int numRecords) {
        }
    }
    
    @Test
    public void testDatabind() throws Exception {
        BulkReadWriteTest.loadCities(new JsonCityWriter());
        // we can add assertions later, for now this test just serves as example code and 
        // ensures no exceptions are thrown
    }

    /** We're going to demonstrate the versitility of Jackson by using and XmlMapper
     * to serialize instead of the default JsonMapper to serialize to json.  Most 
     * importantly, this points to the ability with JacksonHandle or JacksonPojoHandle
     * to bring your own mapper and all the power that comes with it.
     **/
    public static class XmlCityWriter implements CityWriter {
        private int numCities = 0;
        private XMLDocumentManager docMgr = Common.client.newXMLDocumentManager();
        private DocumentWriteSet writeSet = docMgr.newWriteSet();
        private static XmlMapper mapper = new XmlMapper();
        static {
            mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
            mapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);
        }

        public void addCity(City city) {
            if ( numCities >= MAX_TO_WRITE ) return;
            JacksonPojoHandle handle = new JacksonPojoHandle(city);
            // NOTICE: We've set the mapper to an XmlMapper, showing the versitility of Jackson
		    handle.setMapper(mapper);
            handle.setFormat(Format.XML);
            writeSet.add(DIRECTORY + "/xmlCities/" + city.getGeoNameId() + ".xml", handle);
            numCities++;
        }
        public void finishBatch() {
            if ( writeSet.size() > 0 ) {
                docMgr.write(writeSet);
                writeSet = docMgr.newWriteSet();
            }
        }
        public void setNumRecords(int numRecords) {
        }
    }
    
    @Test
    public void testXmlDatabind() throws Exception {
        BulkReadWriteTest.loadCities(new XmlCityWriter());
        // we can add assertions later, for now this test just serves as example code and 
        // ensures no exceptions are thrown
    }

    /* The following fields are in the data but not the third-party pojo */
    @JsonIgnoreProperties({"asciiName", "countryCode2", "dem", "timezoneCode", "lastModified"})
    class ToponymMixIn1 {
    }
    
    /* The following fields are either not in the third party pojo or not in the data so I don't want them serialized*/
    @JsonIgnoreProperties({ 
        "asciiName", "countryCode2", "dem", "timezoneCode", "lastModified",
        "featureClassName", "featureCodeName", "countryName", "adminName1", "adminName2",
        "elevation", "timezone", "style"
    })
    @JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include=JsonTypeInfo.As.WRAPPER_OBJECT)
    class ToponymMixIn2 {
    }

    /** Demonstrate using Jackson's CSV mapper directly to simplify reading in data, populating a 
     * third-party pojo (one we cannot annotate) then writing it out
     * via JacksonPojoHandle with configuration provided by mix-in annotations.
     **/
    @Test
    public void testDatabindingThirdPartyPojoWithMixinAnnotations() throws JsonProcessingException, IOException {
        CsvSchema schema = CsvSchema.builder()
            .setColumnSeparator('\t')
            .addColumn("geoNameId")
            .addColumn("name")
            .addColumn("asciiName")
            .addColumn("alternateNames")
            .addColumn("latitude", CsvSchema.ColumnType.NUMBER)
            .addColumn("longitude", CsvSchema.ColumnType.NUMBER)
            .addColumn("featureClass")
            .addColumn("featureCode")
            .addColumn("countryCode")
            .addColumn("countryCode2")
            .addColumn("adminCode1")
            .addColumn("adminCode2")
            .addColumn("adminCode3")
            .addColumn("adminCode4")
            .addColumn("population")
            .addColumn("elevation", CsvSchema.ColumnType.NUMBER)
            .addColumn("dem", CsvSchema.ColumnType.NUMBER)
            .addColumn("timezoneCode")
            .addColumn("lastModified")
            .build();
        CsvMapper mapper = new CsvMapper();
        mapper.addMixInAnnotations(Toponym.class, ToponymMixIn1.class);
        ObjectReader reader = mapper.reader(Toponym.class).with(schema);
        BufferedReader cityReader = new BufferedReader(Common.testFileToReader(CITIES_FILE));
        GenericDocumentManager docMgr = Common.client.newDocumentManager();
        DocumentWriteSet set = docMgr.newWriteSet();
        String line = null;
        for (int numWritten = 0; numWritten < MAX_TO_WRITE && (line = cityReader.readLine()) != null; numWritten++ ) {
            Toponym city = reader.readValue(line);
            JacksonPojoHandle handle = new JacksonPojoHandle(city);
            handle.getMapper().addMixInAnnotations(Toponym.class, ToponymMixIn2.class);
            set.add(DIRECTORY + "/thirdPartyJsonCities/" + city.getGeoNameId() + ".json", handle);
        }
        docMgr.write(set);
        cityReader.close();
        // we can add assertions later, for now this test just serves as example code and 
        // ensures no exceptions are thrown
    }

    public static void cleanUp() {
        QueryManager queryMgr = Common.client.newQueryManager();
        DeleteQueryDefinition deleteQuery = queryMgr.newDeleteDefinition();
        deleteQuery.setDirectory(DIRECTORY);
        queryMgr.delete(deleteQuery);
    }
}
