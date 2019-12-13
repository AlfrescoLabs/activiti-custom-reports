/**
 * Copyright 2005-2015 Alfresco Software, Ltd. All rights reserved.
 * License rights for this program may be obtained from Alfresco Software, Ltd.
 * pursuant to a written agreement and any use of this program without such an
 * agreement is prohibited.
 */
package org.elasticsearch.client;

import com.activiti.domain.idm.User;
import com.activiti.service.reporting.searchClient.AnalyticsClient;
import com.activiti.service.reporting.searchClient.HighLevelElasticAnalyticsRestClient;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.Mockito.mock;

@Configuration
@ComponentScan(basePackages = {
        "com.activiti.service.reporting.example"
}
)
public class CustomApplicationTestConfiguration {

    /**
     * This is needed to make property resolving work on annotations ...
     * (see http://stackoverflow.com/questions/11925952/custom-spring-property-source-does-not-resolve-placeholders-in-value)
     */
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public AnalyticsClient analyticsClient(RestHighLevelClient restHighLevelClient) {
        return new HighLevelElasticAnalyticsRestClient(restHighLevelClient);
    }

    @Bean
    public RestHighLevelClient restHighLevelClient(RestClient restClient) {
        return new RestHighLevelClient(restClient, RestClient::close, emptyList());
    }

    @Bean
    public RestClient restClient(CloseableHttpAsyncClient httpClient) {
        return new RestClient(httpClient,
                              new Header[0],
                              singletonList(new Node(new HttpHost("localhost"))),
                              "/",
                              new RestClient.FailureListener(),
                              NodeSelector.ANY,
                              false);
    }

    @Bean
    public CloseableHttpAsyncClient httpClient() {
        return mock(CloseableHttpAsyncClient.class);
    }

    @Bean
    public User currentUser() {
        return mock(User.class);
    }
}