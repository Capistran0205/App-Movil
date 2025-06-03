
<?php
/*Esta API se encarga de manejar la conexión inicial de usuarios a una BD de MySQL
  Mediante la asignación de Tokens*/
// API_Conexion.php - API para conectar a MySQL desde Android
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

// Log para debugging (opcional, eliminar en producción)
file_put_contents('php://stderr', "API_Conexion.php called\n");

// Configuración de la base de datos (ajustar según tu PHPMyAdmin)
define('DB_HOST', 'localhost');
define('DB_PORT', '3306');
define('PHPMYADMIN_USER', 'root'); // Usuario con permisos para PHPMyAdmin
define('PHPMYADMIN_PASSWORD', 'Anastasio5602'); // Contraseña del usuario PHPMyAdmin

try {
    // Obtener datos JSON del request
    $rawInput = file_get_contents('php://input');
    file_put_contents('php://stderr', "Raw input: $rawInput\n");
    
    $input = json_decode($rawInput, true);
    
    if (!$input) {
        throw new Exception('Datos JSON inválidos: ' . json_last_error_msg());
    }
    // Recibiendo los parámetros desde la App para realizar la conexión con el servidor
    $database = $input['database'] ?? '';
    $username = $input['username'] ?? '';
    $password = $input['password'] ?? '';
    
    file_put_contents('php://stderr', "Database: $database, Username: $username\n");
    
    if (empty($database) || empty($username) || empty($password)) {
        throw new Exception('Faltan datos de conexión (database, username, password)');
    }
    
    // Validar credenciales con la lista predefinida, es una validación básica pero funcional
    if (!validateUser($username, $password)) {
        throw new Exception('Credenciales inválidas');
    }
    
    // Primero verificar si la base de datos existe
    try {
        $pdo_check = new PDO("mysql:host=" . DB_HOST . ";port=" . DB_PORT, PHPMYADMIN_USER, PHPMYADMIN_PASSWORD, [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION
        ]);
        
        $stmt = $pdo_check->prepare("SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?");
        $stmt->execute([$database]);
        
        if (!$stmt->fetch()) {
            throw new Exception("La base de datos '$database' no existe");
        }
        
    } catch (PDOException $e) {
        throw new Exception("Error verificando base de datos: " . $e->getMessage());
    }
    
    // Conectar a MySQL usando las credenciales de PHPMyAdmin
    $dsn = "mysql:host=" . DB_HOST . ";port=" . DB_PORT . ";dbname=" . $database;
    $pdo = new PDO($dsn, PHPMYADMIN_USER, PHPMYADMIN_PASSWORD, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::MYSQL_ATTR_INIT_COMMAND => "SET NAMES utf8"
    ]);
    
    // Obtener lista de tablas
    $stmt = $pdo->query("SHOW TABLES");
    $tables = $stmt->fetchAll(PDO::FETCH_COLUMN);
    
    // Generar token de sesión
    $sessionToken = generateSessionToken($username, $database);
    
    // Guardar sesión (en un entorno real, usar Redis o base de datos)
    saveSession($sessionToken, [
        'username' => $username,
        'database' => $database,
        'created_at' => time()
    ]);
    
    // Respuesta exitosa. Manda el mensaje, un token de sesión previamente generado y todas la tables de la BD seleccionada
    $response = [
        'success' => true,
        'message' => 'Conexión exitosa',
        'session_token' => $sessionToken,
        'tables' => $tables,
        'database' => $database
    ];
    
    file_put_contents('php://stderr', "Success response: " . json_encode($response) . "\n");
    echo json_encode($response);
    
} catch (PDOException $e) {
    $error_response = [
        'success' => false,
        'message' => 'Error de base de datos: ' . $e->getMessage()
    ];
    file_put_contents('php://stderr', "PDO Error: " . json_encode($error_response) . "\n");
    echo json_encode($error_response);
} catch (Exception $e) {
    $error_response = [
        'success' => false,
        'message' => $e->getMessage()
    ];
    file_put_contents('php://stderr', "General Error: " . json_encode($error_response) . "\n");
    echo json_encode($error_response);
}

function validateUser($username, $password) {
    // Por ejemplo, consultar una tabla de usuarios

    // Lista de usuarios predefinidos, en este caso solo el user root con su respectiva contraseña
    $validUsers = [
        'root' => 'Anastasio5602', // Para testing con usuario root sin contraseña
    ];
    
    return isset($validUsers[$username]) && $validUsers[$username] === $password;
}

function generateSessionToken($username, $database) {
    return hash('sha256', $username . $database . time() . uniqid());
}

function saveSession($token, $data) {
    // En un entorno real, guardar en base de datos o Redis
    // Por ahora, usar archivos temporales los inicios de sesión (no es escalable)
    $sessionFile = sys_get_temp_dir() . '/session_' . $token . '.json';
    $saved = file_put_contents($sessionFile, json_encode($data));
    file_put_contents('php://stderr', "Session saved: $saved bytes to $sessionFile\n");
}
?>