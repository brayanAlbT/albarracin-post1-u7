package com.example.multas;

import com.example.multas.domain.PagoRechazadoException;
import com.example.multas.domain.ResultadoPago;
import com.example.multas.domain.port.PasarelaPagoPort;
import com.example.multas.model.*;
import com.example.multas.repository.MultaRepository;
import com.example.multas.service.MultaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultaServiceTest {

    @Mock
    private MultaRepository multaRepository;

    @Mock
    private PasarelaPagoPort pasarelaPagoPort;

    private MultaService multaService;

    @BeforeEach
    void setUp() {
        multaService = new MultaService(multaRepository, pasarelaPagoPort);
    }

    @Test
    @DisplayName("Punto de decisión 1: Regla pura de cálculo de monto en la entidad Multa")
    void testCalculoMontoEnEntidad() {
        // 5 días * 500 = 2500
        BigDecimal montoNormal = Multa.calcularMonto(5);
        assertEquals(new BigDecimal("2500"), montoNormal);

        // 40 días * 500 = 20000 -> con tope máximo de 15000
        BigDecimal montoTope = Multa.calcularMonto(40);
        assertEquals(new BigDecimal("15000"), montoTope);
    }

    @Test
    @DisplayName("Generar multa exitosamente cuando el estudiante tiene menos de 3 pendientes")
    void testGenerarMultaExito() {
        when(multaRepository.countByEstudianteIdAndEstado("EST-001", EstadoMulta.PENDIENTE)).thenReturn(1L);
        when(multaRepository.save(any(Multa.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Multa creada = multaService.generar("EST-001", "Libro de Cálculo I", 4);

        assertNotNull(creada);
        assertEquals("EST-001", creada.getEstudianteId());
        assertEquals(new BigDecimal("2000"), creada.getMonto());
        assertEquals(EstadoMulta.PENDIENTE, creada.getEstado());
        verify(multaRepository).save(any(Multa.class));
    }

    @Test
    @DisplayName("Punto de decisión 2: Rechazar generación si el estudiante ya acumula 3 multas pendientes")
    void testGenerarMultaLimiteSuperado() {
        when(multaRepository.countByEstudianteIdAndEstado("EST-002", EstadoMulta.PENDIENTE)).thenReturn(3L);

        assertThrows(LimiteMultasPendientesException.class, () ->
                multaService.generar("EST-002", "Química General", 2)
        );

        verify(multaRepository, never()).save(any(Multa.class));
    }

    @Test
    @DisplayName("Pagar en ventanilla exitosamente")
    void testPagarEnVentanilla() {
        Multa multa = new Multa("EST-001", "Física II", 3);
        multa.setId(10L);

        when(multaRepository.findById(10L)).thenReturn(Optional.of(multa));
        when(multaRepository.save(any(Multa.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Multa pagada = multaService.pagarEnVentanilla(10L);

        assertEquals(EstadoMulta.PAGADA, pagada.getEstado());
        assertEquals("VENTANILLA", pagada.getMetodoPago());
        assertNotNull(pagada.getFechaPago());
    }

    @Test
    @DisplayName("Rechazar pago de una multa que ya fue pagada previamente")
    void testPagarMultaYaPagada() {
        Multa multa = new Multa("EST-001", "Física II", 3);
        multa.setId(10L);
        multa.marcarComoPagada("VENTANILLA");

        when(multaRepository.findById(10L)).thenReturn(Optional.of(multa));

        assertThrows(MultaYaPagadaException.class, () -> multaService.pagarEnVentanilla(10L));
        assertThrows(MultaYaPagadaException.class, () -> multaService.pagarConPasarela(10L));
    }

    @Test
    @DisplayName("Parte 2: Pagar en línea exitosamente a través del puerto PasarelaPagoPort")
    void testPagarConPasarelaExito() {
        Multa multa = new Multa("EST-003", "Estructuras de Datos", 6);
        multa.setId(20L);

        when(multaRepository.findById(20L)).thenReturn(Optional.of(multa));
        when(pasarelaPagoPort.procesar(multa))
                .thenReturn(new ResultadoPago("PAGOSUDES", true, "TX-998877", "Pago aprobado"));
        when(multaRepository.save(any(Multa.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Multa pagada = multaService.pagarConPasarela(20L);

        assertEquals(EstadoMulta.PAGADA, pagada.getEstado());
        assertEquals("PAGOSUDES", pagada.getMetodoPago());
        assertNotNull(pagada.getFechaPago());
        verify(pasarelaPagoPort).procesar(multa);
    }

    @Test
    @DisplayName("Parte 2: Rechazo de pago por la pasarela lanza PagoRechazadoException")
    void testPagarConPasarelaRechazo() {
        Multa multa = new Multa("EST-003", "Estructuras de Datos", 6);
        multa.setId(20L);

        when(multaRepository.findById(20L)).thenReturn(Optional.of(multa));
        when(pasarelaPagoPort.procesar(multa))
                .thenReturn(new ResultadoPago("WOMPI", false, null, "Fondos insuficientes"));

        PagoRechazadoException ex = assertThrows(PagoRechazadoException.class, () ->
                multaService.pagarConPasarela(20L)
        );

        assertTrue(ex.getMessage().contains("Fondos insuficientes"));
        assertEquals(EstadoMulta.PENDIENTE, multa.getEstado());
        verify(multaRepository, never()).save(multa);
    }
}
