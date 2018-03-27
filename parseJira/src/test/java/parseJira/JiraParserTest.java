package parseJira;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.Test;

public class JiraParserTest {

     JiraParser jiraParser = new JiraParser();
     
     @Test
     public void aaa() {
         String t1s = "2018-03-09T19:49:30.671+0000";
       DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
       LocalDateTime t1 = LocalDateTime.parse(t1s,f);
       System.out.println(t1s);

     }
     
}