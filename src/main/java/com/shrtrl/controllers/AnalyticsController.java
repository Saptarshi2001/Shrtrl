package com.shrtrl.controllers;

import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.clickhouse.client.api.query.GenericRecord;
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
public ResponseEntity<List<GenericRecord>> liveAnalytics(HttpSession session)
{
UUID userid=(UUID) session.getAttribute("USER_ID");
List<GenericRecord> response=analyticsclient.eventsInLastHour(userid);
return ResponseEntity.ok(response);
}

@GetMapping("/dashboard")
public ResponseEntity<List<GenericRecord>> showDashBoard(HttpSession session,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
UUID userid=(UUID) session.getAttribute("USER_ID");
List<GenericRecord> response=analyticsclient.showanalytics(userid);
return ResponseEntity.ok(response);
}


@GetMapping("/dashboard/day")
public ResponseEntity<List<GenericRecord>> showAnalyticsDay(HttpSession session,@RequestParam LocalDate date,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
        UUID userid=(UUID) session.getAttribute("USER_ID");
List<GenericRecord> response=analyticsclient.clicksToday(userid);
return ResponseEntity.ok(response);
}


@GetMapping("/dashboard/month")
public ResponseEntity<List<GenericRecord>> showAnalyticsMonth(HttpSession session,@RequestParam LocalDate to,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
        UUID userid=(UUID) session.getAttribute("USER_ID");
List<GenericRecord> response=analyticsclient.clicksThisMonth(userid);
return ResponseEntity.ok(response);
}



@GetMapping("/dashboard/year")
public ResponseEntity<List<GenericRecord>> showAnalyticsYear(HttpSession session,@RequestParam LocalDate to,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
        UUID userid=(UUID) session.getAttribute("USER_ID");
List<GenericRecord> response=analyticsclient.clicksThisYear(userid);
return ResponseEntity.ok(response);
}

@GetMapping("/countries")
public ResponseEntity<List<GenericRecord>> showCountry(HttpSession session,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
        UUID userid=(UUID) session.getAttribute("USER_ID");
List<GenericRecord> response=analyticsclient.clicksByCountry(userid);
return ResponseEntity.ok(response);
}


@GetMapping("/states")
public ResponseEntity<List<GenericRecord>> showState(HttpSession session,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
        UUID userid=(UUID) session.getAttribute("USER_ID");
List<GenericRecord> response=analyticsclient.clicksByCountryAndState(userid);
return ResponseEntity.ok(response);
}



}
