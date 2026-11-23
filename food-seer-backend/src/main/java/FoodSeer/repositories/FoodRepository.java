package FoodSeer.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import FoodSeer.entity.Food;

/**
 * Food Repository
 */
public interface FoodRepository extends JpaRepository<Food, Long> {

    Optional<Food> findByFoodName(String foodName);
}
