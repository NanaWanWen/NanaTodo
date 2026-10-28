$headers = @{
    'X-User-Id' = '1'
    'X-Role-Code' = 'ADMIN'
}
$response = Invoke-WebRequest -Uri 'http://localhost:8080/api/options/orders/receivable?keyword=PO202610140001' -Headers $headers -UseBasicParsing
Write-Output $response.Content