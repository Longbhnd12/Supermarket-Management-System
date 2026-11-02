package com.sms.backend.service.payment;

import com.sms.backend.repository.SaleRepository;
import com.sms.backend.service.SaleCompletionService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import static org.mockito.Mockito.*;

class SaleCompletionWorkerTest {
    @Test void recoversPersistedRequestsAndContinuesAfterAnInventoryFailure() {
        SaleRepository sales = mock(SaleRepository.class);
        SaleCompletionService completion = mock(SaleCompletionService.class);
        when(sales.findCompletionRequests(PageRequest.of(0, 25))).thenReturn(List.of(7L, 8L));
        when(completion.complete(7L)).thenThrow(new IllegalStateException("insufficient FEFO stock"));
        new SaleCompletionWorker(sales, completion).recover();
        verify(completion).complete(7L); verify(completion).complete(8L);
        verifyNoMoreInteractions(completion);
    }
}
