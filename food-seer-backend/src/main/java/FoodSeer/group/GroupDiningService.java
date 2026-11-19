package FoodSeer.group;

public interface GroupDiningService {

    GroupDtos.GroupSessionDto createSession(String hostUsername, GroupDtos.CreateSessionRequest request);

    GroupDtos.GroupSessionDto joinSession(String sessionCode, String username, GroupDtos.JoinSessionRequest request);

    GroupDtos.GroupSessionDto getSession(String sessionCode);

    GroupDtos.HarmonizedMenuResponse getHarmonizedMenu(String sessionCode);

    GroupDtos.GroupSessionDto addToTray(String sessionCode, String username, GroupDtos.AddCartItemRequest request);

    GroupDtos.GroupSessionDto removeFromTray(String sessionCode, String username, Long itemId);

    GroupDtos.SplitBillResponse getSplitBill(String sessionCode);

    GroupDtos.CheckoutResponse checkout(String sessionCode, String hostUsername);
}
