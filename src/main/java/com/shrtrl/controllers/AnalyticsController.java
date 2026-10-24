package com.shrtrl.controllers;

import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.shrtrl.DTOs.AnalyticsEventResponse;
import com.shrtrl.DTOs.LiveAnalyticsResponse;
import com.shrtrl.DTOs.ClickSummaryResponse;
import com.shrtrl.DTOs.CountryClicksResponse;
import com.shrtrl.services.AnalyticsService;

import jakarta.servlet.http.HttpSession;


@Controller
@RequestMapping("/api/v1/Analytics")
public class AnalyticsController {

private final AnalyticsService analyticsclient;

public AnalyticsController(AnalyticsService analyticsclient)
{
    this.analyticsclient=analyticsclient;
}
@GetMapping("/dashboard/live")
public ResponseEntity<List<LiveAnalyticsResponse>> liveAnalytics(HttpSession session)
{
UUID userid=(UUID) session.getAttribute("USER_ID");
List<LiveAnalyticsResponse> response=analyticsclient.eventsInLastHour(userid);
return ResponseEntity.status(HttpStatus.OK).body(response);
}

@GetMapping("/dashboard")
public ResponseEntity<List<AnalyticsEventResponse>> showDashBoard(HttpSession session,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
UUID userid=(UUID) session.getAttribute("USER_ID");
List<AnalyticsEventResponse> response=analyticsclient.showanalytics(userid);
return ResponseEntity.status(HttpStatus.OK).body(response);
}


@GetMapping("/dashboard/day")
public ResponseEntity<List<ClickSummaryResponse>> showAnalyticsDay(HttpSession session,@RequestParam LocalDate date,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
UUID userid=(UUID) session.getAttribute("USER_ID");
List<ClickSummaryResponse> response=analyticsclient.clicksToday(userid);
return ResponseEntity.status(HttpStatus.OK).body(response);
}


@GetMapping("/dashboard/month")
public ResponseEntity<List<ClickSummaryResponse>> showAnalyticsMonth(HttpSession session,@RequestParam LocalDate to,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
        UUID userid=(UUID) session.getAttribute("USER_ID");
List<ClickSummaryResponse> response=analyticsclient.clicksThisMonth(userid);
return ResponseEntity.status(HttpStatus.OK).body(response);
}



@GetMapping("/dashboard/year")
public ResponseEntity<List<ClickSummaryResponse>> showAnalyticsYear(HttpSession session,@RequestParam LocalDate to,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
        UUID userid=(UUID) session.getAttribute("USER_ID");
List<ClickSummaryResponse> response=analyticsclient.clicksThisYear(userid);
return ResponseEntity.status(HttpStatus.OK).body(response);
}

@GetMapping("/countries")
public ResponseEntity<List<CountryClicksResponse>> showCountry(HttpSession session,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
        UUID userid=(UUID) session.getAttribute("USER_ID");
List<CountryClicksResponse> response=analyticsclient.clicksByCountry(userid);
return ResponseEntity.status(HttpStatus.OK).body(response);
}


@GetMapping("/states")
public ResponseEntity<List<ClickSummaryResponse>> showState(HttpSession session,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
        UUID userid=(UUID) session.getAttribute("USER_ID");
List<ClickSummaryResponse> response=analyticsclient.clicksByCountryAndState(userid);
return ResponseEntity.status(HttpStatus.OK).body(response);
}



}
