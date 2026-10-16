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

import com.clickhouse.client.api.query.GenericRecord;
import com.clickhouse.client.api.query.QueryResponse;
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
public ResponseEntity<?> liveAnalytics(HttpSession session)
{
UUID userid=session.getAttribute("USER_ID");        
List<GenericRecord> response=analyticsclient.eventsInLastHour(userid);


}

@GetMapping("/dashboard")
public ResponseEntity<?> showDashBoard(HttpSession session,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
UUID userid=(UUID) session.getAttribute("USER_ID");
List<GenericRecord> response=

}


@GetMapping("/dashboard/day")
public ResponseEntity<?> showAnalyticsDay(HttpSession session,@RequestParam LocalDate date,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
        UUID userid=(UUID) session.getAttribute("USER_ID");
List<GenericRecord> response=analyticsclient.clicksToday(userid);

}


@GetMapping("/dashboard/month")
public ResponseEntity<?> showAnalyticsMonth(HttpSession session,@RequestParam LocalDate to,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
        UUID userid=(UUID) session.getAttribute("USER_ID");
List<GenericRecord> response=analyticsclient.clicksThisMonth(userid);

}



@GetMapping("/dashboard/year")
public ResponseEntity<?> showAnalyticsYear(HttpSession session,@RequestParam LocalDate to,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
        UUID userid=(UUID) session.getAttribute("USER_ID");
List<GenericRecord> response=analyticsclient.clicksThisYear(userid);

}


@GetMapping("/dashboard/trend")
public ResponseEntity<?> showTrends(HttpSession session,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{


}


@GetMapping("/countries")
public ResponseEntity<?> showCountry(HttpSession session,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
        UUID userid=(UUID) session.getAttribute("USER_ID");
List<GenericRecord> response=analyticsclient.clicksByCountry(userid);

}


@GetMapping("/states")
public ResponseEntity<?> showState(HttpSession session,@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size )
{
        UUID userid=(UUID) session.getAttribute("USER_ID");
List<GenericRecord> response=analyticsclient.clicksByCountryAndState(userid);

}



}
