package com.devopsdays.qoe.framework.utils;

import com.devopsdays.qoe.framework.models.QoEMetric;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class ApiClient {
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }
    
    public List<Map<String, Object>> getMetrics(String platform, String videoId, String sessionId) throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(baseUrl + "/api/v1/metrics?");
        if (platform != null) url.append("platform=").append(platform).append("&");
        if (videoId != null) url.append("videoId=").append(videoId).append("&");
        if (sessionId != null) url.append("sessionId=").append(sessionId);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), new TypeReference<List<Map<String, Object>>>() {});
        }
        
        throw new IOException("Failed to get metrics: " + response.statusCode());
    }
    
    public Map<String, Object> getSummary(String platform, String videoId) throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(baseUrl + "/api/v1/metrics/summary?");
        if (platform != null) url.append("platform=").append(platform).append("&");
        if (videoId != null) url.append("videoId=").append(videoId);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        }
        
        throw new IOException("Failed to get summary: " + response.statusCode());
    }
}
