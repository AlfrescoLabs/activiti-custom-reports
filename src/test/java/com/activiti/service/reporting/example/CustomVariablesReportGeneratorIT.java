package com.activiti.service.reporting.example;

import com.activiti.domain.idm.User;
import com.activiti.service.api.ReportingIndexManager;
import com.activiti.service.reporting.ReportingIndexManagerImpl;
import com.activiti.service.reporting.searchClient.AnalyticsClient;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.HttpContext;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.CustomApplicationTestConfiguration;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.Sum;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;
import java.util.concurrent.Future;

import static com.activiti.service.reporting.generators.ElasticSearchConstants.INDEX_VARIABLES;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpHeaders.WARNING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Will Abson
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = CustomApplicationTestConfiguration.class)
public class CustomVariablesReportGeneratorIT {

    @Autowired
    private CustomVariablesReportGenerator reportGenerator;

    @Autowired
    private AnalyticsClient analyticsClient;

    @Autowired
    private CloseableHttpAsyncClient httpClient;

    private ReportingIndexManager indexManager = mock(ReportingIndexManagerImpl.class);

    @Autowired
    private User currentUser;

    @Value("classpath:/elasticsearch/variables-count-orders-by-customer-month.json")
    private Resource variablesCountOrdersByCustomerAndMonthJson;

    @Value("classpath:/elasticsearch/variables-customer-orders.json")
    private Resource customerOrdersJson;

    @Value("classpath:/elasticsearch/variables-quantities-by-month.json")
    private Resource quantitiesByMonthJson;

    @Value("classpath:/elasticsearch/variables-orders-by-duedate.json")
    private Resource ordersByDueDateJson;

    private static final String INDEX_NAME = "activiti-test";

    @Before
    public void before() {
        doReturn(INDEX_NAME)
                .when(indexManager)
                .getIndexForUser(eq(currentUser), eq(INDEX_VARIABLES));
    }

    @Test
    public void testCustomerOrderCountsSearch() throws Exception {
        mockElasticSearchRequest(customerOrdersJson);

        SearchResponse response = reportGenerator.search(analyticsClient,
                                                         indexManager,
                                                         currentUser,
                                                         reportGenerator.customerOrderCountsQuery());

        Terms termsAggregation = response.getAggregations().get("customerOrders");
        assertNotNull(termsAggregation);
        List<? extends Terms.Bucket> buckets = termsAggregation.getBuckets();
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
    public void testQuantitiesByMonthSearch() throws Exception {
        mockElasticSearchRequest(quantitiesByMonthJson);

        SearchResponse response = reportGenerator.search(analyticsClient,
                                                         indexManager,
                                                         currentUser,
                                                         reportGenerator.totalQuantityByMonthQuery());

        ParsedDateHistogram aggregation = response.getAggregations().get("ordersByMonth");
        assertNotNull(aggregation);
        List<? extends Histogram.Bucket> buckets = aggregation.getBuckets();
        assertEquals(2, buckets.size());

        // Buckets should be ordered by month
        assertEquals("2019-11-01T00:00Z", buckets.get(0).getKey().toString());
        assertEquals(2, buckets.get(0).getDocCount());
        assertEquals(33, ((Sum) buckets.get(0).getAggregations().get("totalItems")).getValue(), 0);

        assertEquals("2019-12-01T00:00Z", buckets.get(1).getKey().toString());
        assertEquals(1, buckets.get(1).getDocCount());
        assertEquals(6, ((Sum) buckets.get(1).getAggregations().get("totalItems")).getValue(), 0);
    }

    @Test
    public void testTotalOrdersByDueDateSearch() throws Exception {

        mockElasticSearchRequest(ordersByDueDateJson);

        SearchResponse response = reportGenerator.search(analyticsClient,
                                                         indexManager,
                                                         currentUser,
                                                         reportGenerator.totalOrdersByDueDateQuery());

        ParsedDateHistogram aggregation = response.getAggregations().get("ordersByMonthDue");
        assertNotNull(aggregation);
        List<? extends Histogram.Bucket> buckets = aggregation.getBuckets();
        assertEquals(3, buckets.size());

        // Buckets should be ordered by month
        assertEquals("2019-10-01T00:00Z", buckets.get(0).getKey().toString());
        assertEquals(3, buckets.get(0).getDocCount());

        assertEquals("2019-11-01T00:00Z", buckets.get(1).getKey().toString());
        assertEquals(1, buckets.get(1).getDocCount());

        assertEquals("2019-12-01T00:00Z", buckets.get(2).getKey().toString());
        assertEquals(1, buckets.get(2).getDocCount());
    }

    @Test
    public void testNumOrdersByCustomerAndMonthSearch() throws Exception {
        mockElasticSearchRequest(variablesCountOrdersByCustomerAndMonthJson);

        SearchResponse response = reportGenerator.search(analyticsClient,
                                                         indexManager,
                                                         currentUser,
                                                         reportGenerator.numOrdersByCustomerAndMonthQuery());

        ParsedDateHistogram aggregation = response.getAggregations().get("ordersByMonth");
        assertNotNull(aggregation);
        List<? extends Histogram.Bucket> buckets = aggregation.getBuckets();
        assertEquals(2, buckets.size());

        // Buckets should be ordered by month and within that by number of orders
        assertEquals("2019-11-01T00:00Z", buckets.get(0).getKey().toString());
        assertEquals(3, buckets.get(0).getDocCount());

        List<? extends Terms.Bucket> marchTerms = ((Terms) buckets.get(0).getAggregations().get("customerName")).getBuckets();
        assertEquals(3, marchTerms.size());
        assertEquals("Anne", marchTerms.get(0).getKey());
        assertEquals(1, marchTerms.get(0).getDocCount());
        assertEquals("Bob's Store", marchTerms.get(1).getKey());
        assertEquals(1, marchTerms.get(1).getDocCount());
        assertEquals("Charlie Brown", marchTerms.get(2).getKey());
        assertEquals(1, marchTerms.get(2).getDocCount());

        assertEquals("2019-12-01T00:00Z", buckets.get(1).getKey().toString());
        assertEquals(2, buckets.get(1).getDocCount());

        List<? extends Terms.Bucket> aprilTerms = ((Terms) buckets.get(1).getAggregations().get("customerName")).getBuckets();
        assertEquals(2, aprilTerms.size());
        assertEquals("Debbie Dolores", aprilTerms.get(0).getKey());
        assertEquals(2, aprilTerms.get(0).getDocCount());
        assertEquals("Bob's Store", aprilTerms.get(1).getKey());
        assertEquals(1, aprilTerms.get(1).getDocCount());
    }

    private void mockElasticSearchRequest(Resource resource) throws Exception {
        reset(httpClient);
        doReturn(resourceAsHttpResponse(resource))
                .when(httpClient)
                .execute(any(HttpAsyncRequestProducer.class),
                         any(HttpAsyncResponseConsumer.class),
                         any(HttpContext.class),
                         any(FutureCallback.class));
    }

    private Future<HttpResponse> resourceAsHttpResponse(Resource resource) throws Exception {
        HttpEntity mockEntity = mock(HttpEntity.class);
        doReturn(resource.getInputStream())
                .when(mockEntity)
                .getContent();
        doReturn(new BasicHeader(CONTENT_TYPE, "application/json"))
                .when(mockEntity)
                .getContentType();

        StatusLine mockStatusLine = mock(StatusLine.class);
        doReturn(200)
                .when(mockStatusLine)
                .getStatusCode();

        HttpResponse mockResponse = mock(HttpResponse.class);
        doReturn(mockStatusLine)
                .when(mockResponse)
                .getStatusLine();
        doReturn(mockEntity)
                .when(mockResponse)
                .getEntity();
        doReturn(new Header[0])
                .when(mockResponse)
                .getHeaders(eq(WARNING));

        Future<HttpResponse> mockFuture = mock(Future.class);
        doReturn(mockResponse)
                .when(mockFuture)
                .get();
        return mockFuture;
    }
}