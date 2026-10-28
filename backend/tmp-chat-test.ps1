$ErrorActionPreference = 'Continue'

function Ask-Chat($q) {
    "===== 问：$q ====="
    $body = @{ message = $q } | ConvertTo-Json -Compress
    $buf = New-Object System.Collections.Generic.List[string]
    try {
        $req = [System.Net.HttpWebRequest]::Create("http://localhost:8080/api/agent/chat")
        $req.Method = "POST"
        $req.ContentType = "application/json"
        $req.Headers.Add("X-User-Id", "2")
        $req.Headers.Add("X-Role-Code", "BUYER")
        $req.Timeout = 180000
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
        $req.ContentLength = $bytes.Length
        $stream = $req.GetRequestStream()
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Close()
        $resp = $req.GetResponse()
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
        $evt = ""
        while (-not $reader.EndOfStream) {
            $line = $reader.ReadLine()
            if ($line -like "event:*") { $evt = $line.Substring(6).Trim() }
            elseif ($line -like "data:*") {
                if ($evt -eq "done") { $buf.Add($line.Substring(5).Trim()) }
            }
        }
        $reader.Close()
    } catch {
        "ERR $($_.Exception.Message)"
    }
    "答：$($buf -join "`n")"
    ""
}

Ask-Chat "我这个月采购订单完成的咋样"
Ask-Chat "上个月我的采购订单总金额是多少"
Ask-Chat "我这个月采购订单完成三年多的咋样"
