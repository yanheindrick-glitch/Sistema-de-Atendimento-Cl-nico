$securePassword = Read-Host "Digite a senha do banco Supabase" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)

try {
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $env:JAVA_HOME = "C:\Users\pedro\.jdk\jdk-17\jdk-17.0.18"
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    Set-Location $PSScriptRoot
    mvn spring-boot:run
} finally {
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}
