package com.example.multas.domain.port;

import com.example.multas.domain.ResultadoPago;
import com.example.multas.model.Multa;

// domain/port/PasarelaPagoPort.java - Puerto de salida hexagonal (Java puro sin dependencias de Spring ni HTTP)
public interface PasarelaPagoPort {
    ResultadoPago procesar(Multa multa);
}
