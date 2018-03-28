
package parseJira;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.mutable.MutableInt;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.JiraRestClientFactory;
import com.atlassian.jira.rest.client.domain.BasicIssue;
import com.atlassian.jira.rest.client.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.atlassian.util.concurrent.Promise;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.burt.jmespath.Expression;
import io.burt.jmespath.JmesPath;
import io.burt.jmespath.jackson.JacksonRuntime;

public class JiraParser {

    private static String JIRA_URL;
    private static String JIRA_ADMIN_USERNAME;
    private static String JIRA_ADMIN_PASSWORD;
    private static String JIRA_PROJECT;

    private static Map<YearWeek,MutableInt> doneByWeek = new TreeMap<YearWeek,MutableInt>();

    
    public static class YearWeek implements Comparable<YearWeek>{
        int year;
        int week;
        @Override
        public int hashCode() {
            return new HashCodeBuilder().append(year).append(week).toHashCode();
        }
        @Override
        public boolean equals(Object obj) {
            if(obj instanceof YearWeek){
                YearWeek other = (YearWeek)obj;
                return new EqualsBuilder().append(year,other.year).append(week, other.week).isEquals();
            }else return false;
        }

        // - this < other 
        // + this > other
        public int compareTo(YearWeek other) {
            if (year > other.year) return 1;
            if (year == other.year && week>other.week) return 1;
            if (year == other.year && week==other.week) return 0;
            return -1;
        }

        @Override
        public String toString() {
            return year + "-"+ week;
        }
        
    }
    
    public static void main(String[] args) throws URISyntaxException, JsonProcessingException, IOException, InterruptedException, ExecutionException {
        //        JmesPath<JsonNode> jmespath = new JacksonRuntime();
        //        
        //        Expression<JsonNode> expression = jmespath.compile("changelog.histories[?author.name=='...' && (items[?from=='1'] || items[?to=='5'])].created");

        //        ObjectMapper mapper = new ObjectMapper();
        //
        //        JsonNode root = mapper.readTree(new File(System.getProperty("user.home")+"/idea/"+"cd-183.json"));
        //        
        //        JsonNode result = expression.search(root);
        //        
        //        System.out.println(result);
        //        

        // https://stackoverflow.com/questions/29206524/how-to-get-all-issues-of-project-via-jira-rest-java-client

        if (!ArrayUtils.isEmpty(args)) {
            JIRA_URL = args[0];
            JIRA_ADMIN_USERNAME = args[1];
            JIRA_ADMIN_PASSWORD = args[2];
            JIRA_PROJECT = args[3];
            if (args.length == 5) {
                String url = JIRA_URL+"/rest/api/latest/issue/";
                printDuration(args[4], url+args[4]);
                return;
            }
        }
        
        JiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
        URI uri = new URI(JIRA_URL);
        JiraRestClient client = factory.createWithBasicHttpAuthentication(uri, JIRA_ADMIN_USERNAME, JIRA_ADMIN_PASSWORD);
        //Promise<User> promise = client.getUserClient().getUser("admin");
        int startAt=0;
        int total=0;
        int maxResults=50;
        do {
            String jql="project in ("+JIRA_PROJECT+") AND status was \"In progress\" by currentUser() AND status was \"Ready for QA\" by currentUser()";       
            Promise<SearchResult> searchJqlPromise = client.getSearchClient().searchJql(jql,maxResults,startAt);
            SearchResult result = searchJqlPromise.claim();
            //System.out.println(     client.getIssueClient().getIssue("CD-1385").claim().toString() );while
            System.out.println("\n processing next "+(startAt+maxResults));
            for (BasicIssue issue : result.getIssues()) {
                //System.out.println(issue.getKey() +" " + issue.getSelf());
                System.out.print(issue.getKey()+",");
                printDuration(issue.getKey(), issue.getSelf().toString());
            }   
            startAt+=50;
            total =result.getTotal();
        } while (startAt+maxResults < total); 
        

        System.out.println("\n"+doneByWeek);

    }

    public static void printDuration(String key, String issueUrl) throws ClientProtocolException, IOException {

        URI uri = URI.create(issueUrl+"?expand=changelog");


        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(uri.getHost(), uri.getPort()),
                new UsernamePasswordCredentials(JIRA_ADMIN_USERNAME, JIRA_ADMIN_PASSWORD));

        CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();

        HttpHost targetHost = new HttpHost(uri.getHost(), uri.getPort(), "https");

        //Create AuthCache instance
        AuthCache authCache = new BasicAuthCache();
        //Generate BASIC scheme object and add it to the local auth cache
        BasicScheme basicAuth = new BasicScheme();
        authCache.put(targetHost, basicAuth);

        //Add AuthCache to the execution context
        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        context.setAuthCache(authCache);


        try {
            HttpGet httpget = new HttpGet(uri);
            //System.out.println("Executing request " + httpget.getRequestLine());
            CloseableHttpResponse response = httpclient.execute(
                    targetHost, httpget, context);    
            try {
                //System.out.println("----------------------------------------");
                //System.out.println(response.getStatusLine());
                String json=EntityUtils.toString(response.getEntity());
                //System.out.println(json);

                JmesPath<JsonNode> jmespath = new JacksonRuntime();
                Expression<JsonNode> expression = jmespath.compile("changelog.histories[?(items[0].from=='1' && items[0].to=='3' ||items[1].from=='1' && items[1].to=='3'  || items[1].from=='3' && items[1].to=='5' )].{created:created,items:items[?not_null(from)]}[].{created:created,from_0:items[0].from,to_0:items[0].to, from_1:items[1].from,to_1:items[1].to}");
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(json);
                JsonNode result = expression.search(root);
//                System.out.println(key + " "+result);
//                Duration duration=Duration.ZERO;
//                LocalDateTime end=null; 
                int readytoQAWeek=0;
                int year=0;
                int size = result.size();
                for (int i=size-1; i>=0; i-=2) {
                    Optional<String> tsInProgress=getPhaseTs(result.get(i),"1","3");
                    Optional<String> tsReadyQA = (result.get(i-1) != null)? getPhaseTs(result.get(i-1),"3","5") : Optional.empty();
                    if (tsInProgress.isPresent() && tsReadyQA.isPresent()) {
                      //LocalDateTime inProgress = parseTimestamp(tsInProgress.get());
                      LocalDateTime readytoQA = parseTimestamp(tsReadyQA.get());
                      year = readytoQA.getYear();
                      //int inProgressWeek =inProgress.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                      readytoQAWeek =readytoQA.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                      //end = readytoQA;
//                      System.out.println(readytoQA+" "+readytoQAWeek);
                      
//                      duration=duration.plusHours(Duration.between(inProgress,readytoQA).toHours());
                    }
                }                   
//                System.out.println(duration.toHours());
                
                if (readytoQAWeek!=0) {
                    YearWeek yearWeekFound = new YearWeek();
                    yearWeekFound.year = year;
                    yearWeekFound.week = readytoQAWeek;
                    if (doneByWeek.containsKey(yearWeekFound)) doneByWeek.get(yearWeekFound).increment();
                    else {
                        YearWeek yearWeek = new YearWeek();
                        yearWeek.year = year;
                        yearWeek.week = readytoQAWeek;
                        doneByWeek.put(yearWeek, new MutableInt(1));
                    }
                }
                                 
//  https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html
//                LocalDateTime inProgress = parseTimestamp(t1s);
//                LocalDateTime readytoQA = parseTimestamp(t2s);
//                
//                Duration duration = Duration.between(inProgress,readytoQA);
//                System.out.println(key + " "+" inProgress="+inProgress+" readytoQA="+readytoQA+" "+duration.toHours());
                //System.out.println(duration.toHours());

            } finally {
                response.close();
            }
        } finally {
            httpclient.close();
        }



    }

    private static Optional<String> getPhaseTs(JsonNode result, String from, String to) {
        Optional<String> created = getItemTs(result,"from_1",from,"to_1",to);
        if (created.isPresent()) return created;
        created = getItemTs(result,"from_0",from,"to_0",to);
        if (created.isPresent()) return created;
        return Optional.empty();
    }

    private static Optional<String> getItemTs(JsonNode result, String fromLabel,String fromPhase, String toLabel, String toPhase) {
        String from = result.get(fromLabel).asText(); 
        String to = result.get(toLabel).asText(); 
        if (from != null && to !=null && from.equals(fromPhase) && to.equals(toPhase)) {
            return Optional.of(result.get("created").asText()); // 1->3
        }
        return Optional.empty();
    }
    
    protected static LocalDateTime parseTimestamp(String ts) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        return LocalDateTime.parse(ts,f);
    }

}

