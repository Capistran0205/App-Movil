<?php
// AP_EjecucionSQL.php - API para ejecutar consultas SQL
error_reporting(E_ALL);
ini_set('display_errors', 0); // No mostrar errores como HTML

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Accept');

// Manejar preflight OPTIONS request
if ($_SERVER['REQUEST_METHOD'] == 'OPTIONS') {
    http_response_code(200);
    exit();
}

// Configuración de la base de datos.
define('DB_HOST', '127.0.0.1');
define('DB_PORT', '3306');
define('PHPMYADMIN_USER', 'root');
define('PHPMYADMIN_PASSWORD', 'Anastasio5602');

try {
    $rawInput = file_get_contents('php://input');
    file_put_contents('php://stderr', "Execute Query - Raw input: $rawInput\n");
    
    $input = json_decode($rawInput, true);
    
    if (!$input) {
        throw new Exception('Datos JSON inválidos: ' . json_last_error_msg());
    }
    
    $database = $input['database'] ?? '';
    $sessionToken = $input['session_token'] ?? '';
    $query = $input['query'] ?? '';
    
    if (empty($database) || empty($sessionToken) || empty($query)) {
        throw new Exception('Faltan datos requeridos: database, session_token y query');
    }
    
    // Validar sesión
    if (!validateSession($sessionToken)) {
        throw new Exception('Sesión inválida o expirada');
    }
    
    // Validar que la consulta no sea peligrosa
    if (!isSafeQuery($query)) {
        throw new Exception('Consulta no permitida por seguridad');
    }
    
    // Conectar a la base de datos
    $dsn = "mysql:host=" . DB_HOST . ";port=" . DB_PORT . ";dbname=" . $database;
    $pdo = new PDO($dsn, PHPMYADMIN_USER, PHPMYADMIN_PASSWORD, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::MYSQL_ATTR_INIT_COMMAND => "SET NAMES utf8"
    ]);
    
    // Determinar el tipo de consulta
    $queryType = getQueryType($query);
    
    $response = [];
    $startTime = microtime(true);
    /* Validación del tipo de lectura sea de escritura o lectura
       Se prepara las consultas con el propósito de evitar SQL Injection
    */
    switch ($queryType) {
        /* Devuelve los datos y números de filas*/
        case 'SELECT':
            $stmt = $pdo->prepare($query);
            $stmt->execute();
            $results = $stmt->fetchAll();
            // Respues
            $response = [
                'success' => true,
                'query_type' => 'SELECT',
                'data' => $results,
                'row_count' => count($results),
                'execution_time' => round((microtime(true) - $startTime) * 1000, 2) . ' ms'
            ];
            break;
        /* Devuelve filas afectadas (e ID del último registro insertado)*/    
        case 'INSERT':
        case 'UPDATE':
        case 'DELETE':
            $stmt = $pdo->prepare($query);
            $result = $stmt->execute();
            $affectedRows = $stmt->rowCount();
            // Respuesta del servidor a la app
            $response = [
                'success' => true,
                'query_type' => $queryType,
                'affected_rows' => $affectedRows,
                'execution_time' => round((microtime(true) - $startTime) * 1000, 2) . ' ms'
            ];
            
            // Para INSERT, incluir el último ID insertado si aplica
            if ($queryType === 'INSERT') {
                $lastInsertId = $pdo->lastInsertId();
                if ($lastInsertId) {
                    $response['last_insert_id'] = $lastInsertId;
                }
            }
            break;
        /*Confirma ejecución exitosa de creación, modificación o eliminación de tablas*/   
        case 'CREATE':
        case 'ALTER':
        case 'DROP':
            $stmt = $pdo->prepare($query);
            $result = $stmt->execute();
            // Respuesta del servidor a la app en formato Json
            $response = [
                'success' => true,
                'query_type' => $queryType,
                'message' => 'Consulta DDL ejecutada correctamente',
                'execution_time' => round((microtime(true) - $startTime) * 1000, 2) . ' ms'
            ];
            break;
        // Devuelve la información de la esctructura de una BD o tabla    
        case 'SHOW':
        case 'DESCRIBE':
        case 'EXPLAIN':
            $stmt = $pdo->prepare($query);
            $stmt->execute();
            $results = $stmt->fetchAll();
            // Respuesta del servidor a la app en formato Json
            $response = [
                'success' => true,
                'query_type' => $queryType,
                'data' => $results,
                'row_count' => count($results),
                'execution_time' => round((microtime(true) - $startTime) * 1000, 2) . ' ms'
            ];
            break;
            
        default:
            throw new Exception('Tipo de consulta no soportado');
    }
    
    // Actualizar tiempo de última actividad de la sesión
    updateSessionActivity($sessionToken);
    
    file_put_contents('php://stderr', "Query success: " . json_encode($response) . "\n");
    echo json_encode($response);
    
} catch (PDOException $e) {
    $error_response = [
        'success' => false,
        'error_type' => 'database_error',
        'message' => 'Error de base de datos: ' . $e->getMessage()
    ];
    file_put_contents('php://stderr', "Query PDO Error: " . json_encode($error_response) . "\n");
    echo json_encode($error_response);
} catch (Exception $e) {
    $error_response = [
        'success' => false,
        'error_type' => 'general_error',
        'message' => $e->getMessage()
    ];
    file_put_contents('php://stderr', "Query General Error: " . json_encode($error_response) . "\n");
    echo json_encode($error_response);
}

function validateSession($token) {
    // Verificar si existe el archivo de sesión previamente creado
    $sessionFile = sys_get_temp_dir() . '/session_' . $token . '.json';
    
    if (!file_exists($sessionFile)) {
        file_put_contents('php://stderr', "Session file not found: $sessionFile\n");
        return false;
    }
    
    $sessionData = json_decode(file_get_contents($sessionFile), true);
    
    if (!$sessionData) {
        file_put_contents('php://stderr', "Invalid session data\n");
        return false;
    }
    
    // Verificar si la sesión no ha expirado (por ejemplo, 1 hora)
    $sessionTimeout = 3600; // 1 hora en segundos
    if ((time() - $sessionData['created_at']) > $sessionTimeout) {
        // Eliminar sesión expirada
        unlink($sessionFile);
        file_put_contents('php://stderr', "Session expired\n");
        return false;
    }
    
    return true;
}

// Querys permitidas y no permitidas para ejecutar desde la app
function isSafeQuery($query) {
    // Limpiar la consulta
    $query = trim(strtoupper($query));
    
    // Lista de palabras clave peligrosas que no permitimos
    $dangerousKeywords = [
        'DROP DATABASE',
        'DROP SCHEMA',
        'TRUNCATE',
        'DELETE FROM mysql.',
        'DELETE FROM information_schema.',
        'UPDATE mysql.',
        'INSERT INTO mysql.',
        'GRANT',
        'REVOKE',
        'CREATE USER',
        'DROP USER',
        'SET PASSWORD',
        'FLUSH PRIVILEGES'
    ];
    
    foreach ($dangerousKeywords as $keyword) {
        if (strpos($query, $keyword) !== false) {
            return false;
        }
    }
    
    // Permitir consultas básicas
    $allowedKeywords = [
        'SELECT',
        'INSERT',
        'UPDATE',
        'DELETE',
        'CREATE TABLE',
        'ALTER TABLE',
        'DROP TABLE',
        'SHOW',
        'DESCRIBE',
        'DESC',
        'EXPLAIN'
    ];
    
    $isAllowed = false;
    foreach ($allowedKeywords as $keyword) {
        if (strpos($query, $keyword) === 0) {
            $isAllowed = true;
            break;
        }
    }
    
    return $isAllowed;
}
// Obteniendo el tipo de consulta que se realiza desde la app
function getQueryType($query) {
    $query = trim(strtoupper($query));
    
    if (strpos($query, 'SELECT') === 0) return 'SELECT';
    if (strpos($query, 'INSERT') === 0) return 'INSERT';
    if (strpos($query, 'UPDATE') === 0) return 'UPDATE';
    if (strpos($query, 'DELETE') === 0) return 'DELETE';
    if (strpos($query, 'CREATE') === 0) return 'CREATE';
    if (strpos($query, 'ALTER') === 0) return 'ALTER';
    if (strpos($query, 'DROP TABLE') === 0) return 'DROP';
    if (strpos($query, 'SHOW') === 0) return 'SHOW';
    if (strpos($query, 'DESCRIBE') === 0 || strpos($query, 'DESC') === 0) return 'DESCRIBE';
    if (strpos($query, 'EXPLAIN') === 0) return 'EXPLAIN';
    
    return 'UNKNOWN';
}

function updateSessionActivity($token) {
    $sessionFile = sys_get_temp_dir() . '/session_' . $token . '.json';
    
    if (file_exists($sessionFile)) {
        $sessionData = json_decode(file_get_contents($sessionFile), true);
        $sessionData['last_activity'] = time();
        file_put_contents($sessionFile, json_encode($sessionData));
    }
}
?>