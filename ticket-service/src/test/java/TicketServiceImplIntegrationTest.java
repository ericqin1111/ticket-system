
import com.alicp.jetcache.Cache;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.CreateCache;
import com.alicp.jetcache.anno.config.EnableCreateCacheAnnotation;
import com.alicp.jetcache.anno.config.EnableMethodCache;
import org.example.ticket.DTO.PriceTierDetailDTO;
import org.example.ticket.TicketServiceApplication;
import org.example.ticket.config.EmbeddedRedisConfig;

import org.example.ticket.entity.Event;
import org.example.ticket.entity.PriceTier;
import org.example.ticket.entity.Ticket;
import org.example.ticket.mapper.EventMapper;
import org.example.ticket.mapper.PriceTierMapper;
import org.example.ticket.mapper.TicketMapper;
import org.example.ticket.service.TicketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnableCreateCacheAnnotation
@EnableMethodCache(basePackages = "org.example.ticket")
// 启动一个完整的Spring Boot环境进行测试
@SpringBootTest(classes = org.example.ticket.TicketServiceApplication.class,properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
// 导入我们的嵌入式Redis配置
@Import(EmbeddedRedisConfig.class)
class TicketServiceImplIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    // 在集成测试中，我们不希望真的连接到数据库，所以仍然Mock掉Mapper层
    @MockBean
    private PriceTierMapper priceTierMapper;
    @MockBean
    private EventMapper eventMapper;
    @MockBean
    private TicketMapper ticketMapper;

    // 使用@CreateCache可以注入一个JetCache实例，方便我们直接检查缓存内容
    @CreateCache(name = "priceTierCache", cacheType = CacheType.BOTH)
    private Cache<Long, PriceTierDetailDTO> priceTierCache;

    @Test
    @DisplayName("简化测试：验证@Cached是否将结果放入缓存")
    void testCaching_MinimalVersion() {
        // --- 准备阶段 (Arrange) ---
        long priceTierId = 999L; // 使用一个全新的ID，避免受其他测试影响
        PriceTier mockPriceTier = new PriceTier();
        mockPriceTier.setId(priceTierId);
        mockPriceTier.setTierName("简化测试票种");

        // 只需要最基础的Mock
        when(priceTierMapper.selectById(priceTierId)).thenReturn(mockPriceTier);

        // 在调用前，确认缓存是空的
        priceTierCache.remove(priceTierId); // 确保是干净的
        assertNull(priceTierCache.get(priceTierId), "测试开始前，ID为999的缓存应为空");

        // --- 执行阶段 (Act) ---
        System.out.println("--- 开始调用被@Cached注解的方法 ---");
        PriceTierDetailDTO resultDto = ticketService.getPriceTierDetails(priceTierId);
        System.out.println("--- 调用结束，返回的DTO为: " + resultDto);

        assertNotNull(resultDto, "Service方法不应返回null");
        assertEquals("简化测试票种", resultDto.getTierName());


        // --- 断言阶段 (Assert) ---
        System.out.println("--- 开始直接从缓存中获取数据 ---");
        PriceTierDetailDTO cachedDto = priceTierCache.get(priceTierId);
        System.out.println("--- 直接从缓存获取到的DTO为: " + cachedDto);

        assertNotNull(cachedDto, "调用Service方法后，缓存中应存在对应的DTO");
        assertEquals("简化测试票种", cachedDto.getTierName());

        System.out.println("简化测试成功通过！");
    }

    @Test
    @DisplayName("集成测试：验证@Cached多级缓存是否生效")
    void testGetPriceTierDetails_MultiLevelCache_Integration() {
        // --- Arrange ---
        Long priceTierId = 1L;
        PriceTier mockPriceTier = new PriceTier(); // ... (同单元测试的mock数据)
        mockPriceTier.setId(priceTierId);
        mockPriceTier.setEventId(10L);
        mockPriceTier.setTierName("看台A");

        Event mockEvent = new Event();
        mockEvent.setId(10L);
        mockEvent.setTicketId(100L);

        Ticket mockTicket = new Ticket();
        mockTicket.setId(100L);
        mockTicket.setTitle("经典演唱会");

        when(priceTierMapper.selectById(priceTierId)).thenReturn(mockPriceTier);
        when(eventMapper.selectById(10L)).thenReturn(mockEvent);
        when(ticketMapper.selectById(100L)).thenReturn(mockTicket);

        // --- Act & Assert ---

        // 1. 清理缓存，确保测试环境干净
        priceTierCache.remove(priceTierId);
        assertNull(priceTierCache.get(priceTierId), "缓存开始前应为空");

        // 2. 第一次调用：缓存未命中，应查询数据库
        System.out.println("--- 第一次调用 ---");
        PriceTierDetailDTO result1 = ticketService.getPriceTierDetails(priceTierId);

        // 验证数据库被调用了1次
        verify(priceTierMapper, times(1)).selectById(priceTierId);
        assertNotNull(result1);

        // 3. 检查缓存中是否已有数据
        assertNotNull(priceTierCache.get(priceTierId), "调用后，JetCache中应存在缓存");
        assertEquals("看台A", priceTierCache.get(priceTierId).getTierName());

        // 4. 第二次调用：缓存应命中，不应查询数据库
        System.out.println("--- 第二次调用 ---");
        PriceTierDetailDTO result2 = ticketService.getPriceTierDetails(priceTierId);

        // 核心验证：确认数据库方法总共只被调用了1次！
        verify(priceTierMapper, times(1)).selectById(priceTierId);
        assertNotNull(result2);
        assertEquals(result1.getTierName(), result2.getTierName());
    }

    @Test
    @DisplayName("集成测试：验证@CacheUpdate是否更新缓存")
    void testUpdatePriceTier_CacheUpdate_Integration() {
        // --- Arrange ---
        Long priceTierId = 2L;

        // 准备一个初始的DTO并放入缓存
        PriceTierDetailDTO initialDto = new PriceTierDetailDTO();
        initialDto.setTierId(priceTierId);
        initialDto.setTierName("初始名称");
        initialDto.setPrice(new BigDecimal("100.00"));
        priceTierCache.put(priceTierId, initialDto);

        assertEquals("初始名称", priceTierCache.get(priceTierId).getTierName());

        // 准备用于更新的DTO
        PriceTierDetailDTO updatedDto = new PriceTierDetailDTO();
        updatedDto.setTierId(priceTierId);
        updatedDto.setTierName("已更新的名称");
        updatedDto.setPrice(new BigDecimal("200.00"));
        updatedDto.setTotalInventory(99);

        // --- Act ---
        ticketService.updatePriceTier(updatedDto);

        // --- Assert ---
        // 验证数据库更新被调用
        verify(priceTierMapper, times(1)).updateById(any(PriceTier.class));

        // 核心验证：直接从缓存中获取，检查值是否已经被@CacheUpdate更新
        PriceTierDetailDTO dtoInCache = priceTierCache.get(priceTierId);
        assertNotNull(dtoInCache);
        assertEquals("已更新的名称", dtoInCache.getTierName());
        assertEquals(0, new BigDecimal("200.00").compareTo(dtoInCache.getPrice()));
    }

    @Test
    @DisplayName("集成测试：验证@CacheInvalidate是否删除缓存")
    void testEvictPriceTierCacher_CacheInvalidate_Integration() {
        // --- Arrange ---
        Long priceTierId = 3L;
        PriceTierDetailDTO dto = new PriceTierDetailDTO();
        dto.setTierId(priceTierId);
        dto.setTierName("待删除");

        // 先手动放入一个缓存项
        priceTierCache.put(priceTierId, dto);
        assertNotNull(priceTierCache.get(priceTierId), "缓存删除前应存在");

        // --- Act ---
        ticketService.evictPriceTierCacher(priceTierId);

        // --- Assert ---
        // 核心验证：检查缓存是否已被清除
        assertNull(priceTierCache.get(priceTierId), "调用evict后，缓存应被删除");
    }
}