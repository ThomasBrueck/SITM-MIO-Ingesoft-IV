# Sistema de Rutas MIO

Sistema de consulta y visualizacion de rutas del sistema de transporte masivo MIO de Cali. Desarrollado con Java, JavaFX, y ZeroC Ice.

## Descripcion General

Esta aplicacion permite a los usuarios consultar rutas optimas entre paradas del sistema MIO. El sistema esta compuesto por:

- **Servidor ICE**: Gestiona la logica de negocio, carga datos del sistema de transporte y calcula rutas optimas usando algoritmos de busqueda BFS (Breadth-First Search).

- **Cliente JavaFX**: Interfaz grafica que permite seleccionar origen y destino, visualiza rutas en un mapa interactivo y muestra informacion detallada del recorrido.

- **Modelo de Datos**: Grafo que representa 105 rutas, 2119 paradas y 7187 conexiones del sistema MIO.

## Caracteristicas Principales

- **Calculo de rutas optimas**: Encuentra la ruta con menor numero de paradas entre origen y destino.
- **Filtrado inteligente**: Solo muestra destinos alcanzables desde el origen seleccionado.
- **Visualizacion en mapa**: Muestra la ruta calculada en un mapa interactivo usando Leaflet.
- **Informacion detallada**: Lista completa de paradas, distancias, transbordos y lineas necesarias.

## Requisitos del Sistema

### Software Requerido

- **Java Development Kit (JDK) 21**
  - Version minima: OpenJDK 21 o Oracle JDK 21
  - Verificar instalacion: `java -version`

- **Gradle 8.5**
  - El proyecto incluye Gradle Wrapper, no es necesario instalarlo manualmente
  - Se descargara automaticamente al ejecutar `./gradlew`

### Dependencias (se descargan automaticamente)

- **ZeroC Ice 3.7.10**: Middleware para comunicacion cliente-servidor
- **JavaFX 21**: Framework para interfaz grafica
  - Modulos: javafx-controls, javafx-fxml, javafx-web
- **JUnit Jupiter 5.10.0**: Framework de pruebas (solo para desarrollo)

### Puertos de Red

- Puerto 10000: Debe estar disponible para la comunicacion Ice entre servidor y cliente

## Instrucciones de Ejecucion

### 1. Iniciar el Servidor

Abrir una terminal en el directorio del proyecto y ejecutar:

```bash
./gradlew runServer
```

El servidor se iniciara en el puerto 10000 y mostrara:
- Carga de datos del sistema (rutas, paradas, arcos)
- Estadisticas del grafo construido
- Mensaje "SERVIDOR ICE ACTIVO"

### 2. Iniciar el Cliente

Abrir una segunda terminal y ejecutar:

```bash
./gradlew runClient
```

Se abrira la interfaz grafica de la aplicacion.

### 3. Usar la Aplicacion

1. **Seleccionar origen**: Elegir una parada de origen del primer menu desplegable.
2. **Seleccionar destino**: Elegir una parada de destino del segundo menu (solo mostrara paradas alcanzables).
3. **Buscar ruta**: Hacer clic en el boton "Buscar Ruta".
4. **Ver resultados**: La ruta aparecera en el mapa y en la lista de paradas.

## Funcionamiento Tecnico

### Arquitectura Cliente-Servidor

La aplicacion usa el middleware ZeroC Ice para comunicacion entre componentes:

- **GraphService**: Proporciona operaciones sobre el grafo completo (busqueda de rutas, calculo de alcanzabilidad).
- **RouteService**: Proporciona consultas sobre rutas y paradas especificas.

### Algoritmo de Busqueda

El sistema utiliza BFS (Breadth-First Search) para:

1. **Calculo de alcanzabilidad**: Determina todas las paradas accesibles desde un origen.
2. **Busqueda de ruta optima**: Encuentra el camino con menor numero de paradas entre origen y destino.

El algoritmo garantiza encontrar la ruta con menor numero de transbordos posible.

### Modelo de Datos

- **Stops**: Paradas del sistema con coordenadas geograficas y nombres descriptivos.
- **Lines**: Rutas del MIO con identificadores y orientaciones (ida/regreso).
- **Arcs**: Conexiones entre paradas con distancias y pertenencia a lineas especificas.

## Estructura del Proyecto

```
mio/
  app/
    src/
      main/
        java/
          mio/
            client/      # Cliente ICE y logica de conexion
            server/      # Servidor ICE y servicios
            ui/          # Interfaz JavaFX
          Mio/           # Clases generadas por Slice
        resources/
          css/           # Estilos de la interfaz
          data/          # CSV con datos del MIO
          fxml/          # Layouts de JavaFX
          web/           # Mapa HTML con Leaflet
  slice/                 # Definiciones IDL de Ice
  config/                # Archivos de configuracion
```

## Datos del Sistema

Los datos del sistema MIO se cargan desde archivos CSV:

- `stops-241.csv`: 2119 paradas con coordenadas y nombres
- `lines-241.csv`: 105 lineas del sistema
- `linestops-241.csv`: 7187 conexiones entre paradas

## Verificacion de Instalacion

Antes de ejecutar la aplicacion, verificar que Java esta correctamente instalado:

```bash
java -version
```

Deberia mostrar algo similar a:
```
openjdk version "21.0.x" 2024-xx-xx
OpenJDK Runtime Environment (build 21.0.x+xx)
OpenJDK 64-Bit Server VM (build 21.0.x+xx, mixed mode, sharing)
```

Para compilar el proyecto sin ejecutarlo:

```bash
./gradlew build
```

## Solucion de Problemas Comunes

### Error: "Port 10000 already in use"

El puerto 10000 esta siendo usado por otro proceso. Opciones:

1. Detener el proceso que usa el puerto:
   ```bash
   # Linux/macOS
   lsof -ti:10000 | xargs kill -9
   
   # Windows (PowerShell)
   Get-Process -Id (Get-NetTCPConnection -LocalPort 10000).OwningProcess | Stop-Process -Force
   ```

2. O cambiar el puerto en los archivos de configuracion (`config/config.server` y `config/config.client`)

### Error: "java: invalid target release: 21"

La version de Java instalada es inferior a Java 21. Instalar Java 21 o superior.

### Error: "Module javafx.controls not found"

JavaFX no se descargo correctamente. Ejecutar:

```bash
./gradlew clean build --refresh-dependencies
```

### El cliente no se conecta al servidor

Verificar que:
1. El servidor esta ejecutandose (debe mostrar "SERVIDOR ICE ACTIVO")
2. No hay firewall bloqueando el puerto 10000
3. El archivo `config/config.client` apunta a la direccion correcta (localhost:10000 por defecto)

### Error al cargar archivos CSV

Verificar que los archivos CSV existen en `app/src/main/resources/data/`:
- stops-241.csv
- lines-241.csv
- linestops-241.csv

## Notas Importantes

- El servidor debe estar ejecutandose antes de iniciar el cliente.
- El puerto 10000 debe estar disponible para la comunicacion Ice.
- La primera carga de datos puede tomar unos segundos.
- En la primera ejecucion, Gradle descargara todas las dependencias automaticamente (requiere conexion a Internet).

## Autores

Proyecto desarrollado para el curso de Ingenieria de Software IV.
