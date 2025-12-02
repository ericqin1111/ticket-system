//import org.example.ticket.entity.PriceTier;
//import org.example.ticket.mapper.PriceTierMapper;
//import org.example.ticket.service.TicketService;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mock;
//import org.mockito.Mockito;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.boot.test.mock.mockito.SpyBean;
//
//import static org.mockito.Mockito.*;
//
//@SpringBootTest(classes = org.example.ticket.TicketServiceApplication.class)
//public class TicketServiceCacheTest {
//
//    @Autowired
//    private TicketService ticketService;
//
//    // 使用 @SpyBean 来“监视”真实的 Mapper 对象
//    @SpyBean
//    private PriceTierMapper priceTierMapper;
//
//    @Test
//    void testCacheEffect() {
//        long testId = 123L;
//
//        // 首次调用前，假设数据库中存在该数据
//        when(priceTierMapper.selectById(testId)).thenReturn(new PriceTier()); // 返回一个模拟对象
//
//        // 第一次调用
//        System.out.println("--- 第一次调用 ---");
//        ticketService.getPriceTierDetails(testId);
//        // 验证：数据库的 selectById 方法应该被调用了1次
//        verify(priceTierMapper, times(1)).selectById(testId);
//
//        // 第二次调用
//        System.out.println("--- 第二次调用 ---");
//        ticketService.getPriceTierDetails(testId);
//        // 验证：数据库的 selectById 方法总共还是只被调用了1次，证明第二次命中了缓存
//        verify(priceTierMapper, times(1)).selectById(testId);
//
//        // 清除缓存
//        ticketService.evictPriceTierCacher(testId);
//
//        // 清除缓存后再次调用
//        System.out.println("--- 清除缓存后再次调用 ---");
//        ticketService.getPriceTierDetails(testId);
//        // 验证：数据库的 selectById 方法现在总共被调用了2次
//        verify(priceTierMapper, times(2)).selectById(testId);
//    }
//}
