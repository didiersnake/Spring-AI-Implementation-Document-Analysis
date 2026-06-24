package com.retailstore.feedback.controllers;

import com.retailstore.feedback.models.EnhancedFeedback;
import com.retailstore.feedback.models.FeedbackSummary;
import com.retailstore.feedback.services.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.List;

/**
 * Controller for feedback-related endpoints
 */
@Controller
public class feedbackController {

    @Autowired
    private FeedbackService feedbackService;

    /**
     * Display the dashboard page
     *
     * @param model Model to add attributes to
     * @return The name of the view to render
     */
    @GetMapping("/")
    public String dashboard( Model model){
        try{
            FeedbackSummary summary = feedbackService.generateFeedbackSummary();
            model.addAttribute("summary", summary);
            return "dashboard";
        }catch (Exception e){
            model.addAttribute("error", "Error loading feedback data: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Returns all feedback data in JSON format.
     * @return List of enhanced feedback entries
     */
    @GetMapping("/getfeedback")
    @ResponseBody
    public ResponseEntity<List<EnhancedFeedback>> getFeedback() {
        try {
            List<EnhancedFeedback> feedback = feedbackService.getEnhancedFeedback();
            return ResponseEntity.ok(feedback);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}