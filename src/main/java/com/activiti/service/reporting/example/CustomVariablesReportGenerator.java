/**
 * Copyright 2005-2015 Alfresco Software, Ltd. All rights reserved.
 * License rights for this program may be obtained from Alfresco Software, Ltd.
 * pursuant to a written agreement and any use of this program without such an
 * agreement is prohibited.
 */
package com.activiti.service.reporting.example;

import com.activiti.domain.idm.User;
import com.activiti.domain.reporting.*;
import com.activiti.service.api.ReportingIndexManager;
import com.activiti.service.api.UserCache;
import com.activiti.service.reporting.converter.AggsToMultiSeriesChartConverter;
import com.activiti.service.reporting.converter.AggsToSimpleChartBasicConverter;
import com.activiti.service.reporting.converter.AggsToSimpleDateBasedChartBasicConverter;
import com.activiti.service.reporting.converter.BucketExtractors;
import com.activiti.service.reporting.generators.BaseReportGenerator;
import com.activiti.service.reporting.searchClient.AnalyticsClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.activiti.engine.ProcessEngine;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

import static com.activiti.service.reporting.eventhandler.EventFields.PROCESS_DEFINITION_KEY;
import static com.activiti.service.reporting.generators.ElasticSearchConstants.INDEX_VARIABLES;

/**
 * @author Will Abson
 */
@Component(CustomVariablesReportGenerator.ID)
public class CustomVariablesReportGenerator extends BaseReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(CustomVariablesReportGenerator.class);

    public static final String ID = "report.generator.fruitorders";

    public static final String NAME = "Fruit orders overview";

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getTitleKey() {
        return ID;
    }

    @Override
    public String getType() {
        return TYPE_REGULAR_REPORT;
    }

    @Override
    public String getParameters(ObjectMapper objectMapper,
                                Map<String, Object> parameterValues) {
        ParametersDefinition parameters = getParameterDefinitions();
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException e) {
            logger.error("Could not serialise parameters data to JSON " + parameters, e);
            return null;
        }
    }

    public ParametersDefinition getParameterDefinitions() {
        return new ParametersDefinition();
    }

    @Override
    public ReportDataRepresentation generate(ProcessEngine processEngine,
                                             AnalyticsClient analyticsClient,
                                             ReportingIndexManager indexManager,
                                             User currentUser,
                                             UserCache userCache,
                                             ObjectMapper objectMapper,
                                             Map<String, Object> map) {

        // Pie chart - orders by customer
        SearchResponse customerNameResults = search(analyticsClient,
                                                    indexManager,
                                                    currentUser,
                                                    customerOrderCountsQuery());

        // Bar chart - quantities ordered in each month
        SearchResponse ordersByDateResults = search(analyticsClient,
                                                    indexManager,
                                                    currentUser,
                                                    totalQuantityByMonthQuery());

        // Bar chart - num orders by due month
        SearchResponse ordersByDueDate = search(analyticsClient,
                                                indexManager,
                                                currentUser,
                                                totalOrdersByDueDateQuery());

        // Bar chart - num orders by date placed grouped by customer
        SearchResponse ordersPlacedByCustomer = search(analyticsClient,
                                                       indexManager,
                                                       currentUser,
                                                       numOrdersByCustomerAndMonthQuery());

        ReportDataRepresentation reportData = new ReportDataRepresentation();
        reportData.addReportDataElement(generateCustomerOrdersPieChart(customerNameResults));
        reportData.addReportDataElement(generateOrderQuantitiesByMonthChart(ordersByDateResults));
        reportData.addReportDataElement(generateOrdersByDueDateChart(ordersByDueDate));
        reportData.addReportDataElement(generateOrderQuantitiesByMonthAndCustomerChart(ordersPlacedByCustomer));

        return reportData;
    }

    protected SearchSourceBuilder customerOrderCountsQuery() {
        return new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                               .must(new TermQueryBuilder(PROCESS_DEFINITION_KEY, "fruitorderprocess"))
                               .must(new TermQueryBuilder("name", "customername")))
                .aggregation(AggregationBuilders
                                     .terms("customerOrders")
                                     .field("stringValue.keyword"));
    }

    protected SearchSourceBuilder totalQuantityByMonthQuery() {
        return new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                               .must(new TermQueryBuilder(PROCESS_DEFINITION_KEY, "fruitorderprocess"))
                               .must(new TermQueryBuilder("name", "quantity")))
                .aggregation(AggregationBuilders.dateHistogram("ordersByMonth")
                                     .field("createTime")
                                     .format("yyyy-MM")
                                     .calendarInterval(DateHistogramInterval.MONTH)
                                     .subAggregation(AggregationBuilders
                                                             .sum("totalItems")
                                                             .field("longValue")));
    }

    protected SearchSourceBuilder totalOrdersByDueDateQuery() {
        return new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                               .must(new TermQueryBuilder(PROCESS_DEFINITION_KEY, "fruitorderprocess"))
                               .must(new TermQueryBuilder("name", "duedate")))
                .aggregation(AggregationBuilders.dateHistogram("ordersByMonthDue")
                                     .field("dateValue")
                                     .format("yyyy-MM")
                                     .calendarInterval(DateHistogramInterval.MONTH));
    }

    protected SearchSourceBuilder numOrdersByCustomerAndMonthQuery() {
        return new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                               .must(new TermQueryBuilder(PROCESS_DEFINITION_KEY, "fruitorderprocess"))
                               .must(new TermQueryBuilder("name", "customername")))
                .aggregation(AggregationBuilders.dateHistogram("ordersByMonth")
                                     .field("createTime")
                                     .format("yyyy-MM")
                                     .calendarInterval(DateHistogramInterval.MONTH)
                                     .subAggregation(AggregationBuilders
                                                             .terms("customerName")
                                                             .field("stringValue.keyword")));
    }

    protected SearchResponse search(AnalyticsClient analyticsClient,
                                    ReportingIndexManager indexManager,
                                    User currentUser,
                                    SearchSourceBuilder query) {
        SearchRequest request = new SearchRequest(
                indexManager.getIndexForUser(currentUser, INDEX_VARIABLES))
                .source(query);
        try {
            return analyticsClient.search(request);
        } catch (IOException e) {
            logger.error("Error during elastic search", e);
            return null;
        }
    }

    protected PieChartDataRepresentation generateCustomerOrdersPieChart(SearchResponse searchResponse) {

        PieChartDataRepresentation pieChart = new PieChartDataRepresentation();
        pieChart.setTitle("No. of orders by customer");
        pieChart.setDescription("This chart shows the total number of orders placed by each customer");

        new AggsToSimpleChartBasicConverter(searchResponse, "customerOrders").setChartData(
                pieChart,
                new BucketExtractors.BucketKeyExtractor(),
                new BucketExtractors.BucketDocCountExtractor()
        );

        return pieChart;
    }

    protected SingleBarChartDataRepresentation generateOrderQuantitiesByMonthChart(SearchResponse searchResponse) {

        SingleBarChartDataRepresentation chart = new SingleBarChartDataRepresentation();
        chart.setTitle("Total quantities ordered per month");
        chart.setDescription("This chart shows the total number of items that were ordered in each month");
        chart.setyAxisType("count");
        chart.setxAxisType("date_month");

        new AggsToSimpleDateBasedChartBasicConverter(searchResponse, "ordersByMonth").setChartData(
                chart,
                new BucketExtractors.DateHistogramBucketExtractor(),
                new BucketExtractors.BucketAggValueExtractor("totalItems")
        );

        return chart;
    }

    protected SingleBarChartDataRepresentation generateOrdersByDueDateChart(SearchResponse searchResponse) {

        SingleBarChartDataRepresentation chart = new SingleBarChartDataRepresentation();
        chart.setTitle("No. of orders by due date");
        chart.setDescription("This chart shows the number of orders due for fulfilment in each month");
        chart.setyAxisType("count");
        chart.setxAxisType("date_month");

        new AggsToSimpleDateBasedChartBasicConverter(searchResponse, "ordersByMonthDue").setChartData(
                chart,
                new BucketExtractors.DateHistogramBucketExtractor(),
                new BucketExtractors.BucketDocCountExtractor()
        );

        return chart;
    }

    protected MultiBarChart generateOrderQuantitiesByMonthAndCustomerChart(SearchResponse searchResponse) {

        MultiBarChart chart = new MultiBarChart();
        chart.setTitle("Monthly no. of orders by customer");
        chart.setDescription("This chart shows the total number of orders placed by in each month, broken down by customer");
        chart.setyAxisType("count");
        chart.setxAxisType("date_month");

        new AggsToMultiSeriesChartConverter(searchResponse, "ordersByMonth", "customerName").setChartData(
                chart,
                new BucketExtractors.DateHistogramBucketExtractor(),
                new BucketExtractors.BucketDocCountExtractor()
        );

        return chart;
    }
}
