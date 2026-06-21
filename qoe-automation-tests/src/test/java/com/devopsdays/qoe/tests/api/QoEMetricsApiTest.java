package com.devopsdays.qoe.tests.api;

import com.devopsdays.qoe.framework.utils.ApiClient;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class QoEMetricsApiTest {
    private ApiClient apiClient;
    private static final String BASE_URL = System.getProperty("api.base.url", "http://localhost:8080");
    
    @BeforeClass
    public void setUp() {
        apiClient = new ApiClient(BASE_URL);
    }
    
    @Test
    public void testGetMetrics() throws Exception {
        List<Map<String, Object>> metrics = apiClient.getMetrics("web", null, null);
        Assert.assertNotNull(metrics, "Metrics should not be null");
    }
    
    @Test
    public void testGetSummary() throws Exception {
        Map<String, Object> summary = apiClient.getSummary("web", null);
        Assert.assertNotNull(summary, "Summary should not be null");
        Assert.assertTrue(summary.containsKey("totalSessions") || summary.containsKey("message"), 
                "Summary should contain totalSessions or message");
    }
    
    @Test
    public void testHealthEndpoint() throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(BASE_URL + "/actuator/health"))
                .GET()
                .build();
        
        java.net.http.HttpResponse<String> response = client.send(request, 
                java.net.http.HttpResponse.BodyHandlers.ofString());
        
        Assert.assertEquals(response.statusCode(), 200, "Health endpoint should return 200");
    }
}
