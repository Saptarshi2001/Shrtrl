package com.shrtrl.services;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.shrtrl.DTOs.GeoLocation;

class GeoLocationServiceTest {
    @Test
    void mapsProviderCountryAndRegion() {
        var builder = RestClient.builder().baseUrl("https://ipwho.is");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ipwho.is/8.8.8.8?fields=success,country,region"))
                .andRespond(withSuccess("""
                        {"success":true,"country":"United States","region":"California"}
                        """, MediaType.APPLICATION_JSON));
        assertEquals(new GeoLocation("United States", "California"),
                new GeoLocationService(builder.build()).lookup("8.8.8.8"));
        server.verify();
    }

    @Test
    void skipsLocalPrivateAndInvalidAddresses() {
        var builder = RestClient.builder().baseUrl("https://ipwho.is");
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new GeoLocationService(builder.build());
        for (String ip : new String[]{"127.0.0.1", "::1", "10.0.0.1", "192.168.1.2",
                "172.16.0.1", "169.254.1.1", "fc00::1", "100.64.0.1", "localhost", "face", "", "999.999.1.1"}) {
            assertEquals(GeoLocation.UNKNOWN, service.lookup(ip), ip);
        }
        assertEquals(GeoLocation.UNKNOWN, service.lookup(null));
        server.verify();
    }

    @Test
    void providerFailureLeavesLocationUnknown() {
        var builder = RestClient.builder().baseUrl("https://ipwho.is");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ipwho.is/8.8.8.8?fields=success,country,region"))
                .andRespond(withServerError());
        assertEquals(GeoLocation.UNKNOWN, new GeoLocationService(builder.build()).lookup("8.8.8.8"));
        server.verify();
    }

    @Test
    void unsuccessfulLookupLeavesLocationUnknown() {
        var builder = RestClient.builder().baseUrl("https://ipwho.is");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ipwho.is/8.8.8.8?fields=success,country,region"))
                .andRespond(withSuccess("{\"success\":false}", MediaType.APPLICATION_JSON));
        assertEquals(GeoLocation.UNKNOWN, new GeoLocationService(builder.build()).lookup("8.8.8.8"));
        server.verify();
    }
}
