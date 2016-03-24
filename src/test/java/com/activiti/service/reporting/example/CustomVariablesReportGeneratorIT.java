package com.activiti.service.reporting.example;

import com.activiti.test.CustomApplicationTestConfiguration;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogram;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONTokener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static com.activiti.service.reporting.ElasticSearchConstants.TYPE_VARIABLES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Will Abson
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = CustomApplicationTestConfiguration.class)
public class CustomVariablesReportGeneratorIT {

    @Autowired
    CustomVariablesReportGenerator reportGenerator;

    @Value("classpath:/elasticsearch/variables-mapping.json")
    Resource variablesMappingJson;

    @Value("classpath:/elasticsearch/variables-customer-orders.json")
    Resource customerOrdersJson;

    @Value("classpath:/elasticsearch/variables-quantities-by-month.json")
    Resource quantitiesByMonthJson;

    @Value("classpath:/elasticsearch/variables-orders-by-duedate.json")
    Resource ordersByDueDateJson;

    Node node;
    Client client;

    private static final String INDEX_NAME = "activiti-test";

    private static final Logger logger = LoggerFactory.getLogger(CustomVariablesReportGeneratorIT.class);

    @Before
    public void before() {
        node = NodeBuilder.nodeBuilder().local(true).node();
        client = node.client();
    }

    @Test
    public void testCustomerOrderCountsSearch() throws IOException, JSONException {

        createIndexAndRefresh();
        addEntriesAndFlush(customerOrdersJson);

        SearchResponse resp = reportGenerator.executeCustomerOrderCountsSearch(client, INDEX_NAME);

        Terms termsAggregation = resp.getAggregations().get("customerOrders");
        assertNotNull(termsAggregation);
        List<Terms.Bucket> buckets = termsAggregation.getBuckets();
        assertEquals(4, buckets.size());

        // Buckets should be ordered by count descending and then by key alphabetically

        assertEquals("Bob's Store", buckets.get(0).getKey());
        assertEquals(2, buckets.get(0).getDocCount());

        assertEquals("Debbie Dolores", buckets.get(1).getKey());
        assertEquals(2, buckets.get(1).getDocCount());

        assertEquals("Anne", buckets.get(2).getKey());
        assertEquals(1, buckets.get(2).getDocCount());

        assertEquals("Charlie Brown", buckets.get(3).getKey());
        assertEquals(1, buckets.get(3).getDocCount());
    }

    @Test
    public void testQuantitiesByMonthSearch() throws IOException, JSONException {

        createIndexAndRefresh();
        addEntriesAndFlush(quantitiesByMonthJson);

        SearchResponse resp = reportGenerator.executeTotalQuantityByMonthSearch(client, INDEX_NAME);

        DateHistogram aggregation = resp.getAggregations().get("ordersByMonth");
        assertNotNull(aggregation);
        List<? extends DateHistogram.Bucket> buckets = aggregation.getBuckets();
        assertEquals(3, buckets.size());

        // Buckets should be ordered by month

        assertEquals("2016-03", buckets.get(0).getKey());
        assertEquals(2, buckets.get(0).getDocCount());
        assertEquals(33, ((Sum) buckets.get(0).getAggregations().get("totalItems")).getValue(), 0);

        assertEquals("2016-04", buckets.get(1).getKey());
        assertEquals(1, buckets.get(1).getDocCount());
        assertEquals(6, ((Sum) buckets.get(1).getAggregations().get("totalItems")).getValue(), 0);

        assertEquals("2016-05", buckets.get(2).getKey());
        assertEquals(2, buckets.get(2).getDocCount());
        assertEquals(39, ((Sum) buckets.get(2).getAggregations().get("totalItems")).getValue(), 0);
    }

    @Test
    public void testTotalOrdersByDueDateSearch() throws IOException, JSONException {

        createIndexAndRefresh();
        addEntriesAndFlush(ordersByDueDateJson);

        SearchResponse resp = reportGenerator.executeTotalOrdersByDueDateSearch(client, INDEX_NAME);

        DateHistogram aggregation = resp.getAggregations().get("ordersByMonthDue");
        assertNotNull(aggregation);
        List<? extends DateHistogram.Bucket> buckets = aggregation.getBuckets();
        assertEquals(3, buckets.size());

        // Buckets should be ordered by month

        assertEquals("2016-03", buckets.get(0).getKey());
        assertEquals(2, buckets.get(0).getDocCount());

        assertEquals("2016-04", buckets.get(1).getKey());
        assertEquals(1, buckets.get(1).getDocCount());

        assertEquals("2016-05", buckets.get(2).getKey());
        assertEquals(2, buckets.get(2).getDocCount());
    }

    @Test
    public void testNumOrdersByCustomerAndMonthSearch() throws IOException, JSONException {

        createIndexAndRefresh();
        addEntriesAndFlush(customerOrdersJson);

        SearchResponse resp = reportGenerator.executeNumOrdersByCustomerAndMonthSearch(client, INDEX_NAME);

        DateHistogram aggregation = resp.getAggregations().get("ordersByMonth");
        assertNotNull(aggregation);
        List<? extends DateHistogram.Bucket> buckets = aggregation.getBuckets();
        assertEquals(2, buckets.size());

        // Buckets should be ordered by month and within that by number of orders

        assertEquals("2016-03", buckets.get(0).getKey());
        assertEquals(3, buckets.get(0).getDocCount());

        List<Terms.Bucket> marchTerms = ((Terms) buckets.get(0).getAggregations().get("customerName")).getBuckets();
        assertEquals(3, marchTerms.size());
        assertEquals("Anne", marchTerms.get(0).getKey());
        assertEquals(1, marchTerms.get(0).getDocCount());
        assertEquals("Bob's Store", marchTerms.get(1).getKey());
        assertEquals(1, marchTerms.get(1).getDocCount());
        assertEquals("Charlie Brown", marchTerms.get(2).getKey());
        assertEquals(1, marchTerms.get(2).getDocCount());

        assertEquals("2016-04", buckets.get(1).getKey());
        assertEquals(3, buckets.get(1).getDocCount());

        List<Terms.Bucket> aprilTerms = ((Terms) buckets.get(1).getAggregations().get("customerName")).getBuckets();
        assertEquals(2, aprilTerms.size());
        assertEquals("Debbie Dolores", aprilTerms.get(0).getKey());
        assertEquals(2, aprilTerms.get(0).getDocCount());
        assertEquals("Bob's Store", aprilTerms.get(1).getKey());
        assertEquals(1, aprilTerms.get(1).getDocCount());
    }

    @After
    public void after() {
        deleteIndex();
        node.close();
    }

    protected void createIndexAndRefresh() throws IOException {

        String variablesJson = new String(Files.readAllBytes(Paths.get(variablesMappingJson.getURI())));
        client.admin().indices().prepareCreate(INDEX_NAME)
                .addMapping(TYPE_VARIABLES, variablesJson)
                .get();
        client.admin().indices().refresh(new RefreshRequest(INDEX_NAME));
    }

    protected void deleteIndex() {

        DeleteIndexResponse delete = client.admin().indices().delete(new DeleteIndexRequest(INDEX_NAME)).actionGet();
        if (!delete.isAcknowledged()) {
            logger.error("Index wasn't deleted");
        }
    }

    protected void addEntriesAndFlush(Resource jsonFile) throws IOException, JSONException {

        BulkRequestBuilder bulkRequest = client.prepareBulk();
        JSONArray items = (JSONArray) new JSONTokener(new FileReader(jsonFile.getFile())).nextValue();

        for (int i=0; i<items.length(); i++) {
            bulkRequest.add(client
                            .prepareIndex(INDEX_NAME, TYPE_VARIABLES, String.valueOf(i))
                            .setSource(items.get(i).toString().getBytes())
            );
        }

        BulkResponse bulkResponse = bulkRequest.get();
        if (bulkResponse.hasFailures()) {
            logger.warn("Bulk insert had failures");
        }

        client.admin().indices().flush(new FlushRequest(INDEX_NAME)).actionGet();
    }

}