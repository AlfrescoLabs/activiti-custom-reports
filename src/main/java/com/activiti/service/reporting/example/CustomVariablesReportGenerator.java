/**
 * Copyright 2005-2015 Alfresco Software, Ltd. All rights reserved.
 * License rights for this program may be obtained from Alfresco Software, Ltd.
 * pursuant to a written agreement and any use of this program without such an
 * agreement is prohibited.
 */
package com.activiti.service.reporting.example;

import com.activiti.domain.reporting.MultiBarChart;
import com.activiti.domain.reporting.ParametersDefinition;
import com.activiti.domain.reporting.PieChartDataRepresentation;
import com.activiti.domain.reporting.ReportDataRepresentation;
import com.activiti.domain.reporting.SingleBarChartDataRepresentation;
import com.activiti.service.api.UserCache;
import com.activiti.service.reporting.AbstractReportGenerator;
import com.activiti.service.reporting.ElasticSearchConstants;
import com.activiti.service.reporting.converter.AggsToMultiSeriesChartConverter;
import com.activiti.service.reporting.converter.AggsToSimpleChartBasicConverter;
import com.activiti.service.reporting.converter.AggsToSimpleDateBasedChartBasicConverter;
import com.activiti.service.reporting.converter.BucketExtractors;
import org.activiti.engine.ProcessEngine;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.FilteredQueryBuilder;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.TermFilterBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogram;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author Will Abson
 */
@Component(CustomVariablesReportGenerator.ID)
public class CustomVariablesReportGenerator extends AbstractReportGenerator {

    public static final String ID = "report.generator.fruitorders";
    public static final String NAME = "Fruit orders overview";

    private static String PROCESS_DEFINITION_KEY = "Orderprocess";

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ParametersDefinition getParameterDefinitions(Map<String, Object> parameterValues) {
        return new ParametersDefinition();
    }

    @Override
    public ReportDataRepresentation generateReportData(ProcessEngine processEngine,
                                                       Client elasticSearchClient, String indexName, UserCache userCache,
                                                       Map<String, Object> parameterMap) {

        ReportDataRepresentation reportData = new ReportDataRepresentation();

        // Pie chart - orders by customer
        SearchResponse customerNameResults = executeCustomerOrderCountsSearch(elasticSearchClient, indexName);
        reportData.addReportDataElement(generateCustomerOrdersPieChart(customerNameResults));

        // Bar chart - quantities ordered in each month
        SearchResponse ordersByDateResults = executeTotalQuantityByMonthSearch(elasticSearchClient, indexName);
        reportData.addReportDataElement(generateOrderQuantitiesByMonthChart(ordersByDateResults));

        // Bar chart - num orders by due month
        SearchResponse ordersByDueDate = executeTotalOrdersByDueDateSearch(elasticSearchClient, indexName);
        reportData.addReportDataElement(generateOrdersByDueDateChart(ordersByDueDate));

        // Bar chart - num orders by date placed grouped by customer
        SearchResponse ordersPlacedByCustomer = executeNumOrdersByCustomerAndMonthSearch(elasticSearchClient, indexName);
        reportData.addReportDataElement(generateOrderQuantitiesByMonthAndCustomerChart(ordersPlacedByCustomer));

        return reportData;
    }

    protected SearchResponse executeCustomerOrderCountsSearch(Client elasticSearchClient, String indexName) {

        return executeSearch(elasticSearchClient,
                indexName,
                ElasticSearchConstants.TYPE_VARIABLES,
                new FilteredQueryBuilder(
                        new MatchAllQueryBuilder(),
                        FilterBuilders.andFilter(
                                new TermFilterBuilder("processDefinitionKey", PROCESS_DEFINITION_KEY),
                                new TermFilterBuilder("name._exact_name", "customername")
                        )
                ),
                AggregationBuilders.terms("customerOrders").field("stringValue._exact_string_value")
        );
    }

    protected SearchResponse executeTotalQuantityByMonthSearch(Client elasticSearchClient, String indexName) {

        return executeSearch(elasticSearchClient,
                indexName,
                ElasticSearchConstants.TYPE_VARIABLES,
                new FilteredQueryBuilder(
                        new MatchAllQueryBuilder(),
                        FilterBuilders.andFilter(
                                new TermFilterBuilder("processDefinitionKey", PROCESS_DEFINITION_KEY),
                                new TermFilterBuilder("name._exact_name", "quantity")
                        )
                ),
                AggregationBuilders.dateHistogram("ordersByMonth")
                        .field("createTime")
                        .format("yyyy-MM")
                        .interval(DateHistogram.Interval.MONTH)
                        .subAggregation(AggregationBuilders.sum("totalItems").field("longValue"))
        );
    }

    protected SearchResponse executeTotalOrdersByDueDateSearch(Client elasticSearchClient, String indexName) {

        return executeSearch(elasticSearchClient,
                indexName,
                ElasticSearchConstants.TYPE_VARIABLES,
                new FilteredQueryBuilder(
                        new MatchAllQueryBuilder(),
                        FilterBuilders.andFilter(
                                new TermFilterBuilder("processDefinitionKey", PROCESS_DEFINITION_KEY),
                                new TermFilterBuilder("name._exact_name", "duedate")
                        )
                ),
                AggregationBuilders.dateHistogram("ordersByMonthDue")
                        .field("dateValue")
                        .format("yyyy-MM")
                        .interval(DateHistogram.Interval.MONTH)
        );
    }

    protected SearchResponse executeNumOrdersByCustomerAndMonthSearch(Client elasticSearchClient, String indexName) {

        return executeSearch(elasticSearchClient,
                indexName,
                ElasticSearchConstants.TYPE_VARIABLES,
                new FilteredQueryBuilder(
                        new MatchAllQueryBuilder(),
                        FilterBuilders.andFilter(
                                new TermFilterBuilder("processDefinitionKey", PROCESS_DEFINITION_KEY),
                                new TermFilterBuilder("name._exact_name", "customername")
                        )
                ),
                AggregationBuilders.dateHistogram("ordersByMonth")
                        .field("createTime")
                        .format("yyyy-MM")
                        .interval(DateHistogram.Interval.MONTH)
                        .subAggregation(AggregationBuilders.terms("customerName").field("stringValue._exact_string_value"))
        );
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
