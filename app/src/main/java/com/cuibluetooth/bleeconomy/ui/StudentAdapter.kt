package com.cuibluetooth.bleeconomy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.cuibluetooth.bleeconomy.databinding.ItemStudentRowBinding
import com.cuibluetooth.bleeconomy.model.Student


class StudentAdapter(
    private val onTrackingToggled: (Student, Boolean) -> Unit
) : ListAdapter<StudentAdapter.StudentListItem, StudentAdapter.StudentViewHolder>(DiffCallback) {
    data class StudentListItem(val student: Student, val isTracked: Boolean)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemStudentRowBinding.inflate(inflater, parent, false)
        return StudentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class StudentViewHolder(private val binding: ItemStudentRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StudentListItem) {
            val student = item.student
            binding.studentCode.text =
                student.institutionalCode?.takeIf { it.isNotBlank() } ?: "—"
            binding.studentName.text =
                student.name?.takeIf { it.isNotBlank() } ?: student.username
            binding.studentProject.text =
                student.projectName?.takeIf { it.isNotBlank() } ?: "—"
            binding.studentCheckbox.setOnCheckedChangeListener(null)
            binding.studentCheckbox.isChecked = item.isTracked
            binding.studentCheckbox.setOnCheckedChangeListener { _, isChecked ->
                (bindingAdapter as? StudentAdapter)?.onTrackingToggled?.invoke(student, isChecked)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<StudentListItem>() {
            override fun areItemsTheSame(oldItem: StudentListItem, newItem: StudentListItem): Boolean =
                oldItem.student.id == newItem.student.id

            override fun areContentsTheSame(oldItem: StudentListItem, newItem: StudentListItem): Boolean =
                oldItem == newItem
        }
    }
}
