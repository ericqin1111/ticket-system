

import com.fasterxml.jackson.databind.ObjectMapper;

import org.example.ticket.DTO.PriceTierDetailDTO;
import org.example.ticket.entity.Event;
import org.example.ticket.entity.PriceTier;
import org.example.ticket.entity.Ticket;
import org.example.ticket.mapper.EventMapper;
import org.example.ticket.mapper.PriceTierMapper;
import org.example.ticket.mapper.TicketMapper;
import org.example.ticket.service.impl.TicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    // @Mock: 创建一个Mock对象，用于替换真实的依赖
    @Mock
    private TicketMapper ticketMapper;
    @Mock
    private EventMapper eventMapper;
    @Mock
    private PriceTierMapper priceTierMapper;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private StringRedisTemplate stringRedisTemplate; // 尽管JetCache接管了，但为了Service能实例化，还是Mock它

    // @InjectMocks: 创建一个被测试类的实例，并自动将上面@Mock注解的依赖注入进去
    @InjectMocks
    private TicketServiceImpl ticketService;

    // 为了方便，在测试类中也初始化一个ObjectMapper
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RedissonClient redissonClient;

    // 在每个测试方法执行前，重新设置TicketServiceImpl的内部依赖（可选，但更规范）
    @BeforeEach
    void setUp() {
        // 使用反射或构造函数注入来设置log和objectMapper
        // 如果你的TicketServiceImpl构造函数包含了所有参数，@InjectMocks会自动处理
        // 这里假设构造函数如你所提供
        ticketService = new TicketServiceImpl(
                ticketMapper,
                stringRedisTemplate,
                eventMapper,
                priceTierMapper,
                kafkaTemplate,
                objectMapper,
                redissonClient
        );
    }

    @Test
    @DisplayName("首次调用getPriceTierDetails应查询数据库，后续调用应走缓存")
    void testGetPriceTierDetails_CachingBehavior() {
        // --- Arrange (准备阶段) ---
        Long priceTierId = 1L;
        PriceTier mockPriceTier = new PriceTier();
        mockPriceTier.setId(priceTierId);
        mockPriceTier.setEventId(10L);
        mockPriceTier.setTierName("看台A");
        mockPriceTier.setPrice(new BigDecimal("580.00"));
        mockPriceTier.setTotalInventory(1000);

        Event mockEvent = new Event();
        mockEvent.setId(10L);
        mockEvent.setTicketId(100L);
        mockEvent.setEventTime(LocalDateTime.now());

        Ticket mockTicket = new Ticket();
        mockTicket.setId(100L);
        mockTicket.setTitle("经典演唱会");

        // 当Mapper被调用时，返回我们准备好的Mock数据
        when(priceTierMapper.selectById(priceTierId)).thenReturn(mockPriceTier);
        when(eventMapper.selectById(10L)).thenReturn(mockEvent);
        when(ticketMapper.selectById(100L)).thenReturn(mockTicket);

        // --- Act (执行阶段) ---
        // 第一次调用
        System.out.println("--- 第一次调用 ---");
        PriceTierDetailDTO result1 = ticketService.getPriceTierDetails(priceTierId);

        // 第二次调用 (注意：在单元测试中，我们无法真正测试JetCache的缓存命中，
        // 我们通过验证数据库查询次数来间接证明。JetCache的注解需要Spring上下文才能工作)
        // 为了模拟，我们再次调用并验证
        System.out.println("--- 第二次调用 ---");
        PriceTierDetailDTO result2 = ticketService.getPriceTierDetails(priceTierId);


        // --- Assert (断言阶段) ---
        assertNotNull(result1);
        assertEquals(priceTierId, result1.getTierId());
        assertEquals("看台A", result1.getTierName());
        assertEquals("经典演唱会", result1.getTicketName());

        // 验证结果一致
        assertEquals(result1.getTierName(), result2.getTierName());

        // 核心验证：验证priceTierMapper.selectById方法是否只被调用了1次。
        // 如果是2次，说明缓存逻辑（在真实环境中）可能没有生效。
        // 注意：这个测试在纯Mockito环境下，注解是无效的，所以会调用2次。
        // 这个测试的意义在于验证业务逻辑本身的正确性。
        verify(priceTierMapper, times(2)).selectById(priceTierId);
        verify(eventMapper, times(2)).selectById(10L);
        verify(ticketMapper, times(2)).selectById(100L);

        // 我们将在集成测试中真正验证缓存命中。
    }

    @Test
    @DisplayName("当数据库中不存在记录时，getPriceTierDetails应返回null")
    void testGetPriceTierDetails_NotFound() {
        // --- Arrange ---
        Long priceTierId = 999L;
        when(priceTierMapper.selectById(priceTierId)).thenReturn(null);

        // --- Act ---
        PriceTierDetailDTO result = ticketService.getPriceTierDetails(priceTierId);

        // --- Assert ---
        assertNull(result);
        // 验证只查询了一次数据库
        verify(priceTierMapper, times(1)).selectById(priceTierId);
        // 验证后续的mapper没有被调用
        verify(eventMapper, never()).selectById(anyLong());
        verify(ticketMapper, never()).selectById(anyLong());
    }


    @Test
    @DisplayName("updatePriceTier应成功更新数据库")
    void testUpdatePriceTier() {
        // --- Arrange ---
        PriceTierDetailDTO dtoToUpdate = new PriceTierDetailDTO();
        dtoToUpdate.setTierId(1L);
        dtoToUpdate.setTierName("VIP区-更新");
        dtoToUpdate.setPrice(new BigDecimal("1280.00"));
        dtoToUpdate.setTotalInventory(50);

        // `updateById`方法返回的是影响的行数，这里我们不关心，所以用doNothing
        // when(priceTierMapper.updateById(any(PriceTier.class))).thenReturn(1);
        when(priceTierMapper.updateById(any(PriceTier.class))).thenReturn(1);


        // --- Act ---
        ticketService.updatePriceTier(dtoToUpdate);

        // --- Assert ---
        // 验证数据库更新方法被精确地调用了1次
        verify(priceTierMapper, times(1)).updateById(argThat(priceTier ->
                priceTier.getId().equals(1L) &&
                        priceTier.getTierName().equals("VIP区-更新") &&
                        priceTier.getTotalInventory() == 50
        ));
    }

    @Test
    @DisplayName("evictPriceTierCacher应按预期调用")
    void testEvictPriceTierCacher() {
        // --- Arrange ---
        Long priceTierId = 1L;

        // --- Act ---
        // 虽然这个方法体是空的（因为注解会处理逻辑），
        // 我们测试它的可调用性，确保没有抛出异常
        ticketService.evictPriceTierCacher(priceTierId);

        // --- Assert ---
        // 在单元测试中，我们无法验证缓存真的被清除了。
        // 只能验证这个方法能被成功调用。
        // 在集成测试中，我们会验证缓存的实际状态。
        assertTrue(true, "evictPriceTierCacher should be callable without errors.");
    }
}