package com.retailstore.feedback.services;

import com.retailstore.feedback.models.EnhancedFeedback;
import com.retailstore.feedback.models.FeedbackEntry;
import com.retailstore.feedback.models.FeedbackSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for processing feedback data and enhancing it with AI insights
 */
@Service
public class FeedbackService {
    @Autowired
    private GeminiAIService geminiAIService;

    //Path to sentiment analysis output file
    private static final String SENTIMENT_FILE_PATH = "sentiment_feedback_output.txt";

    // Patterns for parsing feedback file
    private static final Pattern FEEDBACK_PATTERN = Pattern.compile("Feedback #(\\d+)");
    private static final Pattern CUSTOMER_PATTERN = Pattern.compile("Customer:\\s*(.+)");
    private static final Pattern DEPARTMENT_PATTERN = Pattern.compile("Department:\\s*(.+)");
    private static final Pattern DATE_PATTERN = Pattern.compile("Date:\\s*(.+)");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("Comment:\\s*(.+)");
    private static final Pattern SENTIMENT_PATTERN = Pattern.compile("Sentiment:\\s*(.+)");

    //Cache for feedback data
    private List<EnhancedFeedback> enhancedFeedbackCache = null;

    /**
     * Reads feedback data from the sentiment analysis output file.
     *
     * @return List of feedbackEntry objects
     * @throws IOException if the file cannot be read
     */
    public List<FeedbackEntry> readFeedbackData() throws IOException {
        List<FeedbackEntry> entries = new ArrayList<>();

        try(BufferedReader reader = new BufferedReader( new FileReader(SENTIMENT_FILE_PATH))){
            StringBuilder entryText = new StringBuilder();
            String line;

            boolean inDetailedSection = false;
            while((line = reader.readLine()) != null){
                //Check if we've reached the detailed feedback section
                if(line.contains("## Detailed Feedback Entries")){
                    inDetailedSection = true;
                    continue;
                }
                if(!inDetailedSection){
                    continue;
                }

                //Process each line
                if(line.trim().isEmpty() && entryText.length() > 0){
                    //We've reached the end of an entry
                    FeedbackEntry entry = parseFeedbackEntry(entryText.toString());
                    if(entry != null){
                        entries.add(entry);
                    }
                    entryText = new StringBuilder();
                }else{
                    entryText.append(line).append("\n");
                }
            }
            // Process the last entry if there is one
            if (entryText.length() > 0) {
                FeedbackEntry entry = parseFeedbackEntry(entryText.toString());
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    /**
     * Parses a feedback entry from text.
     *
     * @param text the text containing the feedback entry
     * @return a FeedbackEntry object or null if parsing fails
     */
    private FeedbackEntry parseFeedbackEntry(String text){
        FeedbackEntry entry = new FeedbackEntry();

        // Extract feedback ID
        Matcher idMatcher = FEEDBACK_PATTERN.matcher(text);
        if (idMatcher.find()) {
            entry.setId(Integer.parseInt(idMatcher.group(1)));
        } else {
            return null;
        }

        // Extract customer
        Matcher customerMatcher = CUSTOMER_PATTERN.matcher(text);
        if (customerMatcher.find()) {
            entry.setCustomer(customerMatcher.group(1));
        }

        // Extract department
        Matcher departmentMatcher = DEPARTMENT_PATTERN.matcher(text);
        if (departmentMatcher.find()) {
            entry.setDepartment(departmentMatcher.group(1));
        }

        // Extract date
        Matcher dateMatcher = DATE_PATTERN.matcher(text);
        if (dateMatcher.find()) {
            entry.setDate(dateMatcher.group(1));
        }

        // Extract comment
        Matcher commentMatcher = COMMENT_PATTERN.matcher(text);
        if (commentMatcher.find()) {
            entry.setComment(commentMatcher.group(1));
        }

        // Extract sentiment
        Matcher sentimentMatcher = SENTIMENT_PATTERN.matcher(text);
        if (sentimentMatcher.find()) {
            entry.setSentiment(sentimentMatcher.group(1));
        }

        return entry;
    }

    /**
     * Enhances feedback with AI generated category and actionable insights.
     *
     * @return List of EnhancedFeedback objects
     * @throws IOException if the file cannot be read
     */
    public synchronized List<EnhancedFeedback> getEnhancedFeedback() throws IOException {
        //Return cached data if available
        if(enhancedFeedbackCache != null){
            return enhancedFeedbackCache;
        }

        List<FeedbackEntry> entries = readFeedbackData();
        List<EnhancedFeedback> enhancedEntries = new ArrayList<>();

        for(FeedbackEntry entry: entries){
            EnhancedFeedback enhancedEntry = enhanceFeedback(entry);
            enhancedEntries.add(enhancedEntry);
        }

        //Cache the enhanced feedback
        enhancedFeedbackCache = enhancedEntries;
        return enhancedEntries;
    }

    /**
     * Enhances a single feedback entry with AI generated category and actionable insights.
     *
     * @param entry FeedbackEntry to enhance
     * @return EnhancedFeedback with AI-generated category and actionable insights
     */
    private EnhancedFeedback enhanceFeedback(FeedbackEntry entry){
        EnhancedFeedback enhancedEntry = new EnhancedFeedback(entry);

        //Create a prompt for Gemini
        String prompt = String.format("""
                You are an AI assistant specialized in customer feedback analysis.
                Analyze the following customer feedback and:
                1. Categorize the feedback into one of these categories: Product Quality, Customer Service, Store Experience, Website/App, Delivery, Price/Value, Inventory/Stock, or Other.
                2. Provide a specific actionable insight or recommendation based on the feedback.
    
                Format your response as JSON with two fields: "category" and "actionableInsight".
                Keep your response concise but insightful.
    
                Customer Feedback:
                Comment: %s
                Department: %s
                Sentiment: %s
    
                Provide the category and actionable insight as JSON:
                """, entry.getComment(), entry.getDepartment(), entry.getSentiment());
        try{
            //Call Gemini API and parse the response
            String response = geminiAIService.generateContent(prompt);

            //Parse the JSON response
            String jsonResponse = response.trim();

            //Extract category
            Pattern categoryPattern = Pattern.compile("\"category\"\\s*:\\s*\"([^\"]+)\"");
            Matcher categoryMatcher = categoryPattern.matcher(jsonResponse);
            if(categoryMatcher.find()){
                enhancedEntry.setCategory(categoryMatcher.group(1));
            }else{
                enhancedEntry.setCategory("Uncategorized");
            }

            // Extract actionable insight
            Pattern insightPattern = Pattern.compile("\"actionableInsight\"\\s*:\\s*\"([^\"]+)\"");
            Matcher insightMatcher = insightPattern.matcher(jsonResponse);
            if (insightMatcher.find()) {
                enhancedEntry.setActionableInsight(insightMatcher.group(1));
            } else {
                enhancedEntry.setActionableInsight("No specific action recommended.");
            }
            Thread.sleep(1000);
        }
        catch (InterruptedException e){
            Thread.currentThread().interrupt();
//            break;
        }
        catch (Exception e){
            // Handle API errors gracefully
            enhancedEntry.setCategory("Error in processing");
            enhancedEntry.setActionableInsight("Could not generate insight due to API error: " + e.getMessage());
        }
        return enhancedEntry;
    }

    /**
     * Generates a summary of the feedback data for the dashboard
     *
     * @return FeedbackSummary object containing summary data
     * @throws IOException if summary generation fails
     */
    public FeedbackSummary generateFeedbackSummary() throws IOException {
       List<EnhancedFeedback> allFeedback = getEnhancedFeedback();
       FeedbackSummary summary = new FeedbackSummary();

       //Set total feedback count
        summary.setTotalFeedback(allFeedback.size());

        //Count sentiment
        Map<String, Integer> sentimentCount = new HashMap<>();
        for(EnhancedFeedback feedback: allFeedback){
            String sentiment = feedback.getSentiment();
            sentimentCount.put(sentiment, sentimentCount.getOrDefault(sentiment, 0) + 1);
        }
        summary.setSentimentCounts(sentimentCount);

        //Count categories
        Map<String, Integer> categoryCount = new HashMap<>();
        for(EnhancedFeedback feedback: allFeedback){
            String category = feedback.getCategory();
            categoryCount.put(category, categoryCount.getOrDefault(category, 0) + 1);
        }
        summary.setCategoryCounts(categoryCount);

        // Count departments
        Map<String, Integer> departmentCounts = new HashMap<>();
        for (EnhancedFeedback feedback : allFeedback) {
            String department = feedback.getDepartment();
            departmentCounts.put(department, departmentCounts.getOrDefault(department, 0) + 1);
        }
        summary.setDepartmentCounts(departmentCounts);

        // Get recent feedback (last 5 entries)
        List<EnhancedFeedback> recentFeedback = allFeedback.stream()
                .sorted(Comparator.comparing(EnhancedFeedback::getId).reversed())
                .limit(5)
                .collect(Collectors.toList());
        summary.setRecentFeedback(recentFeedback);
        return summary;
    }

}