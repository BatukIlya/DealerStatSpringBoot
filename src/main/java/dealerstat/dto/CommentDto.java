package dealerstat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentDto {

    @NotBlank
    @Size(max = 500)
    private String message;

    @DecimalMin(value = "0.00001")
    @DecimalMax(value = "5.0")
    private Double rating;

}
