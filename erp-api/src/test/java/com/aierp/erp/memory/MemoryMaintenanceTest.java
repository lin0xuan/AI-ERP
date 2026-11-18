package com.aierp.erp.memory;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;

class MemoryMaintenanceTest {
    @Test void embeddingFailureStillForgets() {
        MemoryStore store=mock(MemoryStore.class);
        ConversationMemory memory=mock(ConversationMemory.class);
        MemoryModel model=mock(MemoryModel.class);
        when(store.idle()).thenReturn(List.of());
        when(store.oldSessions()).thenReturn(List.of());
        when(store.users()).thenReturn(List.of(1L));
        when(store.acquire(1L)).thenReturn("lease");
        when(model.embeddingIdentity()).thenReturn("v1");
        when(store.active(1L)).thenReturn(List.of(Map.of("id","m","kind","fact","content","预算100元","source_id",1L,"importance",2,"explicit_memory",0)));
        when(model.embed(anyString())).thenThrow(new IllegalStateException("down"));
        new MemoryMaintenance(store,memory,model).tick();
        verify(store).maintain(1L);
        verify(store).release(1L,"lease");
        verify(store,never()).embedding(anyLong(),anyString(),anyLong(),anyString(),anyString());
    }
}
