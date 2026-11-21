package FoodSeer.mealplan;

import java.util.Optional;

/**
 * A chat model that answers with one JSON object. Implementations never throw:
 * any failure (model down, timeout, bad response) is an empty result, and the
 * planner falls back to the keyword reader.
 */
public interface LlmClient {

    /**
     * @param system
     *            instructions
     * @param user
     *            the request
     * @return the model's raw JSON reply, or empty on any failure
     */
    Optional<String> completeJson(String system, String user);

    /** @return model name, such as "qwen2.5:1.5b" */
    String model();
}
