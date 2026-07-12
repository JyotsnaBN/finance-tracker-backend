package com.financetracker.constants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryKeywords {
    
    public static final Map<String, List<String>> CATEGORY_KEYWORDS = new HashMap<>() {{
        put("Food", List.of(
            "swiggy", "zomato", "uber eats", "dominos", "pizza", "kfc", "mcdonald",
            "restaurant", "cafe", "food", "dmart", "hungerbox", "cut coffee",
            "fresh", "meat", "bakery", "sweet", "juice", "coffee", "tea",
            "reliance fresh", "more", "spencers", "nature's basket", "foodhall"
        ));

        put("Groceries", List.of(
            "grocery", "bigbasket", "blinkit", "dunzo", "grofers", "jiomart",
            "instamart", "zepto", "milk", "vegetables", "fruits"
        ));
                
        put("Transport", List.of(
            "uber", "ola", "rapido", "metro", "bus", "taxi", "auto", "rickshaw",
            "petrol", "fuel", "diesel", "gas", "parking", "toll", "fastag",
            "bike", "car", "vehicle", "transport", "indian oil", "hp", "bharat petroleum",
            "shell", "essar", "nayara"
        ));
        
        put("Shopping", List.of(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "snapdeal",
            "mall", "store", "shopping", "retail", "fashion", "clothing",
            "shoes", "electronics", "mobile", "laptop", "gadget", "nykaa",
            "tata cliq", "shoppers stop", "lifestyle", "westside", "pantaloons"
        ));
        
        put("Bills", List.of(
            "electricity", "water", "gas", "lpg", "internet", "broadband",
            "mobile", "recharge", "dth", "airtel", "jio", "vodafone", "bsnl",
            "bill", "utility", "payment", "subscription", "tata sky", "dish tv",
            "sun direct", "d2h", "bescom", "mseb", "cesc", "adani"
        ));
        
        put("Entertainment", List.of(
            "netflix", "prime", "hotstar", "disney", "spotify", "youtube",
            "movie", "cinema", "pvr", "inox", "theatre", "game", "gaming",
            "music", "concert", "event", "ticket", "bookmyshow", "paytm insider",
            "zee5", "sonyliv", "voot", "mx player", "jio cinema",
            "bigtree entertainment", "bigtree"
        ));
        
        put("Healthcare", List.of(
            "pharmacy", "hospital", "doctor", "medical", "medicine", "drug",
            "apollo", "medplus", "health", "clinic", "lab", "test", "checkup",
            "insurance", "mediclaim", "netmeds", "1mg", "pharmeasy", "practo",
            "fortis", "max", "manipal", "narayana"
        ));
        
        put("Education", List.of(
            "school", "college", "university", "course", "udemy", "coursera",
            "books", "tuition", "coaching", "training", "education", "learning",
            "exam", "fee", "byju", "unacademy", "vedantu", "toppr", "khan academy",
            "upgrad", "simplilearn"
        ));
        
        put("Income", List.of(
            "salary", "freelance", "payment received", "refund", "cashback",
            "interest", "dividend", "bonus", "incentive", "commission", "credit",
            "received", "deposit", "transfer in"
        ));
        
        put("Investment", List.of(
            "mutual fund", "sip", "stock", "share", "equity", "bond",
            "gold", "silver", "crypto", "bitcoin", "investment", "trading",
            "zerodha", "groww", "upstox", "angel one", "paytm money",
            "kuvera", "etmoney", "coin", "smallcase"
        ));
        
        put("Insurance", List.of(
            "insurance", "premium", "policy", "lic", "hdfc life", "icici pru",
            "term", "health insurance", "car insurance", "bike insurance",
            "max life", "sbi life", "bajaj allianz", "star health", "care health"
        ));
        
        put("Travel", List.of(
            "flight", "hotel", "booking", "makemytrip", "goibibo", "cleartrip",
            "yatra", "airbnb", "oyo", "treebo", "fabhotel", "indigo", "spicejet",
            "air india", "vistara", "irctc", "train", "bus ticket", "redbus"
        ));
        
        put("Personal Care", List.of(
            "salon", "spa", "gym", "fitness", "yoga", "beauty", "haircut",
            "massage", "wellness", "cult fit", "healthifyme", "urban company",
            "lakme", "vlcc", "jawed habib"
        ));
    }};
    
    private CategoryKeywords() {
    }
}
