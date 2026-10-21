package com.shrtrl.services;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.shrtrl.DTOs.GeoLocation;

@Service
public class GeoLocationService {
    private static final Logger logger = LoggerFactory.getLogger(GeoLocationService.class);
    private final RestClient client;

    @Autowired
    public GeoLocationService() {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.client = RestClient.builder()
                .baseUrl("https://ipwho.is")
                .requestFactory(factory)
                .build();
    }

    GeoLocationService(RestClient client) {
        this.client = client;
    }

    public GeoLocation lookup(String ip) {
        if (!isPublicAddress(ip)) {
            return GeoLocation.UNKNOWN;
        }
        try {
            var response = client.get()
                    .uri("/{ip}?fields=success,country,region", ip)
                    .retrieve()
                    .body(LookupResponse.class);
            if (response == null || !response.success()) {
                return GeoLocation.UNKNOWN;
            }
            return new GeoLocation(response.country(), response.region());
        } catch (RestClientException exception) {
            // Location is optional; a provider outage must not prevent the redirect.
            logger.warn("IP geolocation lookup failed: {}", exception.getClass().getSimpleName());
            return GeoLocation.UNKNOWN;
        }
    }

    private boolean isPublicAddress(String ip) {
        // Accept only IP literals, so this check never resolves user-provided host names.
        if (ip == null || ip.isBlank()
                || !(ip.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")
                        || (ip.contains(":") && ip.matches("[0-9a-fA-F:]+")))) {
            return false;
        }
        try {
            var address = InetAddress.getByName(ip);
            byte[] bytes = address.getAddress();
            return !address.isAnyLocalAddress() && !address.isLoopbackAddress()
                    && !address.isSiteLocalAddress() && !address.isLinkLocalAddress()
                    && !address.isMulticastAddress()
                    && !(bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc)
                    && !(bytes.length == 4 && (bytes[0] & 0xff) == 100
                            && (bytes[1] & 0xc0) == 64);
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    public record LookupResponse(boolean success, String country, String region) {}
}
