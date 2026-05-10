package four_tential.potential.domain.review.review;


// 후기 요약에 필요한 rating + content 묶음

public record ReviewSummaryItem(int rating, String content) {
}