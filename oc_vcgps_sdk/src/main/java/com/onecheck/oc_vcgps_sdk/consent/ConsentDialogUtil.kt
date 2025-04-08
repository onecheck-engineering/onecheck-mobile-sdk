package com.onecheck.oc_vcgps_sdk.consent

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast

object ConsentDialogUtil {

    fun showConsentDialog(context: Context, onAgree: () -> Unit) {
        AlertDialog.Builder(context)
//            .setTitle("위치 정보 수집 동의")
//            .setMessage(
//                "본 앱은 일부 지역 매장에 대한 방문 여부 및 체류 시간을\n" +
//                        "익명으로 수집하는 기능을 포함하고 있습니다.\n\n" +
//                        "사용자의 위치 정보를 기반으로 방문 기록을 저장하며,\n" +
//                        "수집된 데이터는 위치 기반 서비스 개선을 위한\n" +
//                        "통계 분석 목적으로 활용됩니다.\n\n" +
//                        "수집 항목:\n" +
//                        "• 현재 위치 정보 (약 20~30m 정확도)\n" +
//                        "• 해시 처리된 디바이스 정보 (비식별화)\n" +
//                        "• 앱 및 SDK 버전 정보\n\n" +
//                        "※ 수집은 등록된 일부 매장에 한정되며,\n" +
//                        "모든 매장에서 이뤄지는 것은 아닙니다.\n\n" +
//                        "해당 정보는 앱이 백그라운드 상태일 때도\n" +
//                        "주기적으로 수집될 수 있습니다.\n\n" +
//                        "본 수집에 동의하지 않으셔도 앱의 주요 기능은\n" +
//                        "정상적으로 이용 가능합니다.\n" +
//                        "단, 방문 기반 분석 기능은 제한될 수 있습니다.\n\n" +
//                        "위 내용에 동의하시겠습니까?"
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
                "本アプリは一部地域の店舗における訪問状況や滞在時間を\n" +
                        "匿名で収集する機能を含んでいます。\n\n" +
                        "ユーザーの位置情報に基づいて訪問記録を保存し、\n" +
                        "収集されたデータは位置情報サービスの改善を目的とした\n" +
                        "統計分析に使用されます。\n\n" +
                        "収集項目：\n" +
                        "• 現在の位置情報（精度：約20～30m）\n" +
                        "• ハッシュ化されたデバイス情報（非識別化）\n" +
                        "• アプリおよびSDKのバージョン情報\n\n" +
                        "※ 収集は登録された一部の店舗に限定され、\n" +
                        "すべての店舗で行われるわけではありません。\n\n" +
                        "この情報は、アプリがバックグラウンド状態でも\n" +
                        "定期的に収集される可能性があります。\n\n" +
                        "本収集に同意されない場合でも、アプリの主要な機能は\n" +
                        "通常通りご利用いただけます。\n" +
                        "ただし、訪問分析機能には制限が生じる可能性があります。\n\n" +
                        "上記内容に同意いただけますか？"
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