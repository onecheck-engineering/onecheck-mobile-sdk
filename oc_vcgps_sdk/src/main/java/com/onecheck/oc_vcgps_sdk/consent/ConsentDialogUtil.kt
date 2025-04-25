package com.onecheck.oc_vcgps_sdk.consent

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object ConsentDialogUtil {

    fun showConsentDialog(context: Context, onAgree: () -> Unit) {
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(context)
//            .setTitle("위치 정보 수집 동의")
//            .setMessage(
//        "본 앱은 사용자의 위치 정보를 바탕으로,\n" +
//                "일부 지역 내 활동 기록 및 체류 정보를 익명으로 수집하는 기능을 포함하고 있습니다.\n\n" +
//                "수집된 정보는 위치 기반 서비스의 품질 향상, 운영 효율 분석,\n" +
//                "향후 서비스 개선을 위한 데이터 기반 의사결정 등에 활용될 수 있습니다.\n\n" +
//                "[수집 항목]\n" +
//                "• 현재 위치 정보 (약 20~30m 정확도)\n" +
//                "• 해시 처리된 디바이스 정보 (비식별화)\n" +
//                "• 앱 및 SDK 버전 정보\n\n" +
//                "※ 수집은 특정 지역 또는 조건에서 제한적으로 수행되며,\n" +
//                "모든 위치 정보가 자동으로 수집되지는 않습니다.\n\n" +
//                "해당 정보는 앱이 백그라운드 상태일 때도\n" +
//                "주기적으로 수집될 수 있습니다.\n\n" +
//                "본 수집에 동의하지 않으셔도 앱의 주요 기능은\n" +
//                "정상적으로 이용 가능합니다.\n\n" +
//                "위 내용에 동의하시겠습니까?"
//            )
//            .setPositiveButton("확인하고 동의") { dialog, _ ->
//                onAgree() // 동의 처리 콜백
//                dialog.dismiss()
//            }
//            .setNegativeButton("동의하지 않음") { dialog, _ ->
//                Toast.makeText(
//                    context,
//                    "방문 분석 기능이 비활성화됩니다.",
//                    Toast.LENGTH_LONG
//                ).show()
//                dialog.dismiss()
//            }
                .setTitle("位置情報の収集に関する同意")
                .setMessage(
                    "本アプリは、ユーザーの位置情報に基づき、\n" +
                            "一部地域での活動記録や滞在情報を匿名で収集する機能を含んでいます。\n\n" +
                            "収集された情報は、位置情報サービスの品質向上、運営の効率分析、\n" +
                            "今後のサービス改善のためのデータに基づく意思決定などに活用されます。\n\n" +
                            "[収集項目]\n" +
                            "• 現在の位置情報（精度：約20〜30m）\n" +
                            "• ハッシュ化されたデバイス情報（非識別化）\n" +
                            "• アプリおよびSDKのバージョン情報\n\n" +
                            "※ 収集は特定の地域または条件に限定して実施され、\n" +
                            "すべての位置情報が自動的に収集されるわけではありません。\n\n" +
                            "この情報はアプリがバックグラウンドで動作している場合でも、\n" +
                            "定期的に収集される可能性があります。\n\n" +
                            "この収集に同意されない場合でも、\n" +
                            "アプリの主要な機能は通常通りご利用いただけます。\n\n" +
                            "上記の内容に同意いただけますか？"
                )
                .setPositiveButton("内容を確認して同意する") { dialog, _ ->
                    onAgree() // 同意処理のコールバック
                    dialog.dismiss()
                }
                .setNegativeButton("同意しない") { dialog, _ ->
                    Toast.makeText(
                        context,
                        "訪問分析機能が無効になります。",
                        Toast.LENGTH_LONG
                    ).show()
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
        }
    }
}